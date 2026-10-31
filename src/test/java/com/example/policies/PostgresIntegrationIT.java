package com.example.policies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.policies.adapters.out.persistence.JdbcDurableOperationRepository;
import com.example.policies.adapters.out.persistence.JdbcOperationProcessingRepository;
import com.example.policies.adapters.out.persistence.JdbcOutboxRepository;
import com.example.policies.application.error.IdempotencyConflictException;
import com.example.policies.application.model.CoreOperationResult;
import com.example.policies.application.model.CoreOperationStatus;
import com.example.policies.application.port.out.DurableOperationRepository;
import com.example.policies.application.port.out.OperationProcessingRepository;
import com.example.policies.application.port.out.OutboxRepository;
import com.example.policies.application.usecase.IssuePolicyCommand;
import com.example.policies.application.usecase.IssueRiskCommand;
import com.example.policies.application.usecase.OperationDraft;
import com.example.policies.application.usecase.OperationSnapshot;
import com.example.policies.application.usecase.OperationWork;
import com.example.policies.application.usecase.OutboxEvent;
import com.example.policies.application.usecase.PolicyCancellationCommand;
import com.example.policies.application.usecase.PolicyRenewalCommand;
import com.example.policies.application.usecase.RenewedRiskValue;
import com.example.policies.application.usecase.RiskCancellationCommand;
import com.example.policies.application.usecase.RiskCreationCommand;
import com.example.policies.domain.model.MoneyCurrency;
import com.example.policies.domain.model.OperationStatus;
import com.example.policies.domain.model.PartyType;
import com.example.policies.domain.model.PolicyType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgresIntegrationIT {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.4-alpine3.20")
          .waitingFor(
              Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2)
                  .withStartupTimeout(Duration.ofMinutes(3)));

  private static Flyway flyway;
  private static JdbcTemplate jdbcTemplate;
  private static DurableOperationRepository operationRepository;
  private static OperationProcessingRepository operationProcessingRepository;
  private static TransactionTemplate transactionTemplate;

  @BeforeAll
  static void migrateSchema() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    flyway = Flyway.configure().dataSource(dataSource).load();
    flyway.migrate();
    jdbcTemplate = new JdbcTemplate(dataSource);
    operationRepository = new JdbcDurableOperationRepository(jdbcTemplate);
    operationProcessingRepository = new JdbcOperationProcessingRepository(jdbcTemplate);
    transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  @Test
  void appliesInitialFlywayMigrationAndCreatesDomainTables() {
    assertEquals("001", flyway.info().current().getVersion().getVersion());

    Set<String> expectedTables =
        Set.of(
            "parties",
            "policies",
            "risks",
            "policy_periods",
            "period_risk_values",
            "ipc_rates",
            "operations",
            "outbox_events",
            "audit_events",
            "notification_deliveries");
    Set<String> actualTables =
        new HashSet<>(
            jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                    + "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
                String.class));

    assertEquals(expectedTables, actualTables);
  }

  @Test
  void enforcesPartyDocumentUniqueness() {
    String documentReference = "INTEGRATION-" + UUID.randomUUID();
    insertParty(UUID.randomUUID(), documentReference, PartyType.PERSON.name());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> insertParty(UUID.randomUUID(), documentReference, PartyType.PERSON.name()));
  }

  @Test
  void rejectsInvalidPartyTypeAtDatabaseBoundary() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> insertParty(UUID.randomUUID(), "INVALID-TYPE-" + UUID.randomUUID(), "INVALID"));
  }

  @Test
  void enforcesOperationIdempotencyAtDatabaseBoundary() {
    String idempotencyKey = "integration-" + UUID.randomUUID();
    insertOperation(UUID.randomUUID(), idempotencyKey);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> insertOperation(UUID.randomUUID(), idempotencyKey));
  }

  @Test
  void atomicallyStoresOperationOutboxAndAuditAndReturnsExistingReplay() {
    OperationDraft draft = operationDraft("ATOMIC-" + UUID.randomUUID(), "a".repeat(64));
    OperationSnapshot first =
        transactionTemplate.execute(status -> operationRepository.createIdempotently(draft));
    assertNotNull(first);

    OperationSnapshot replay =
        transactionTemplate.execute(
            status ->
                operationRepository.createIdempotently(
                    operationDraft(draft.idempotencyKey(), draft.requestHash())));
    assertNotNull(replay);
    assertEquals(first.id(), replay.id());
    assertEquals(1, countRows("operations", first.id()));
    assertEquals(1, countRows("outbox_events", first.id()));
    assertEquals(1, countRows("audit_events", first.id()));

    assertThrows(
        IdempotencyConflictException.class,
        () ->
            transactionTemplate.execute(
                status ->
                    operationRepository.createIdempotently(
                        operationDraft(draft.idempotencyKey(), "b".repeat(64)))));
  }

  @Test
  void rollsBackOperationOutboxAndAuditTogether() {
    OperationDraft draft = operationDraft("ROLLBACK-" + UUID.randomUUID(), "c".repeat(64));

    assertThrows(
        ForcedRollback.class,
        () ->
            transactionTemplate.execute(
                status -> {
                  operationRepository.createIdempotently(draft);
                  throw new ForcedRollback();
                }));
    assertEquals(0, countRows("operations", draft.id()));
    assertEquals(0, countRows("outbox_events", draft.id()));
    assertEquals(0, countRows("audit_events", draft.id()));
  }

  @Test
  void claimsOutboxEventWithLeaseAndMarksItPublishedAfterBrokerConfirm() {
    jdbcTemplate.update(
        "UPDATE outbox_events SET published_at = CURRENT_TIMESTAMP WHERE published_at IS NULL");
    UUID operationId = UUID.randomUUID();
    insertOperation(operationId, "OUTBOX-" + UUID.randomUUID());
    UUID eventId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO outbox_events "
            + "(id, operation_id, event_type, payload, available_at, created_at) "
            + "VALUES (?, ?, 'POLICY_ISSUE_REQUESTED', '{}'::jsonb, ?, ?)",
        eventId,
        operationId,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
    OutboxRepository outboxRepository = new JdbcOutboxRepository(jdbcTemplate);

    List<OutboxEvent> claimed = outboxRepository.claimReadyBatch(10, "relay-test");

    assertEquals(1, claimed.size());
    assertEquals(eventId, claimed.getFirst().id());
    assertTrue(outboxRepository.markPublished(claimed.getFirst(), "relay-test"));
    assertFalse(outboxRepository.markPublished(claimed.getFirst(), "relay-test"));
    assertTrue(outboxRepository.claimReadyBatch(10, "relay-test-2").isEmpty());
  }

  @Test
  void manualReconciliationRequeuesQueryWithoutChangingOperationIdentity() {
    UUID operationId = UUID.randomUUID();
    String idempotencyKey = "RECONCILE-" + operationId;
    insertOperation(operationId, idempotencyKey);
    jdbcTemplate.update("UPDATE operations SET status = 'NEEDS_REVIEW' WHERE id = ?", operationId);

    OperationSnapshot requeued =
        operationRepository.requestReconciliation(operationId, "integration-reviewer");

    assertEquals(OperationStatus.RECONCILING, requeued.status());
    assertEquals(1, countRows("operations", operationId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox_events "
                + "WHERE operation_id = ? AND event_type = 'CORE_RECONCILIATION_REQUESTED'",
            Integer.class,
            operationId));
  }

  @Test
  void resumesWaitingRenewalAndReleasesContractOperationForTheWorker() {
    PolicyFixture fixture = createPolicyFixture(1);
    UUID operationId = UUID.randomUUID();
    insertContractOperation(operationId, "RENEW_POLICY", fixture.policyId(), null);
    jdbcTemplate.update(
        "UPDATE operations SET status = 'WAITING_INPUT', requested_payload = ?::jsonb WHERE id = ?",
        "{\"rate_required\":true}",
        operationId);
    String resumedPayload =
        "{\"policy_id\":\""
            + fixture.policyId()
            + "\",\"source_period_id\":\""
            + fixture.periodId()
            + "\",\"new_period_id\":\""
            + UUID.randomUUID()
            + "\",\"rate_required\":false}";

    OperationSnapshot resumed =
        operationRepository.resumeWaitingInput(operationId, resumedPayload, "integration-operator");

    assertEquals(OperationStatus.PENDING, resumed.status());
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox_events "
                + "WHERE operation_id = ? AND event_type = 'POLICY_RENEWAL_REQUESTED'",
            Integer.class,
            operationId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_events "
                + "WHERE entity_id = ? AND action = 'RENEWAL_RESUMED'",
            Integer.class,
            operationId));
    jdbcTemplate.update("UPDATE operations SET status = 'NEEDS_REVIEW' WHERE id = ?", operationId);
    assertTrue(operationRepository.hasInFlightOperation(fixture.policyId()));
  }

  @Test
  void persistsContractAndSyntheticNotificationOnlyAfterCoreConfirmation() {
    UUID operationId = UUID.randomUUID();
    UUID holderId = UUID.randomUUID();
    UUID landlordId = UUID.randomUUID();
    insertParty(holderId, "WORKER-HOLDER-" + operationId, PartyType.PERSON.name());
    insertParty(landlordId, "WORKER-LANDLORD-" + operationId, PartyType.PERSON.name());
    insertOperation(operationId, "WORKER-ISSUE-" + operationId);
    String requestHash = "d".repeat(64);
    jdbcTemplate.update(
        "UPDATE operations SET request_hash = ? WHERE id = ?", requestHash, operationId);
    OperationWork operation =
        transactionTemplate.execute(
            status ->
                operationProcessingRepository.claim(operationId, "worker-test").orElseThrow());
    IssuePolicyCommand request =
        new IssuePolicyCommand(
            PolicyType.INDIVIDUAL,
            holderId,
            java.time.LocalDate.parse("2026-10-30"),
            12,
            List.of(new IssueRiskCommand("Synthetic address", holderId, landlordId, "1500000.00")),
            "synthetic test policy");
    CoreOperationResult confirmation =
        new CoreOperationResult(
            operationId, requestHash, CoreOperationStatus.CONFIRMED, "CORE-TEST-001", "confirmed");

    transactionTemplate.executeWithoutResult(
        status ->
            operationProcessingRepository.completePolicyIssue(operation, request, confirmation));

    UUID policyId =
        jdbcTemplate.queryForObject(
            "SELECT policy_id FROM operations WHERE id = ?", UUID.class, operationId);
    assertNotNull(policyId);
    assertEquals(
        "SUCCEEDED",
        jdbcTemplate.queryForObject(
            "SELECT status FROM operations WHERE id = ?", String.class, operationId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM risks WHERE policy_id = ? AND status = 'ACTIVE'",
            Integer.class,
            policyId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM period_risk_values "
                + "WHERE monthly_rent = 1500000.00 AND premium = 18000000.00 AND currency = 'COP' "
                + "AND period_id IN (SELECT id FROM policy_periods WHERE policy_id = ?)",
            Integer.class,
            policyId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM notification_deliveries n "
                + "JOIN outbox_events e ON e.id = n.outbox_event_id "
                + "WHERE e.operation_id = ? AND n.recipient = 'demo-operator@example.invalid'",
            Integer.class,
            operationId));
  }

  @Test
  void rejectsNegativeIpcRatesAtDatabaseBoundary() {
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "INSERT INTO ipc_rates "
                    + "(id, reference_period, effective_on, rate, source, is_fictional, created_at) "
                    + "VALUES (?, ?, CURRENT_DATE, ?, ?, ?, ?)",
                UUID.randomUUID(),
                "NEGATIVE-" + UUID.randomUUID(),
                new BigDecimal("-0.01000"),
                "synthetic integration fixture",
                true,
                Timestamp.from(Instant.now())));
  }

  @Test
  void persistsRiskAdditionOnlyAfterCoreConfirmation() {
    PolicyFixture fixture = createPolicyFixture(2);
    UUID operationId = UUID.randomUUID();
    insertContractOperation(operationId, "ADD_RISK", fixture.policyId(), fixture.periodId());
    OperationWork operation = claimOperation(operationId);
    RiskCreationCommand request =
        new RiskCreationCommand(
            fixture.policyId(),
            fixture.periodId(),
            "Additional synthetic property",
            fixture.holderId(),
            fixture.landlordId(),
            "2500000.00");

    transactionTemplate.executeWithoutResult(
        status ->
            operationProcessingRepository.completeRiskAddition(
                operation, request, confirmed(operation, "CORE-RISK-ADD-001")));

    assertEquals(
        3,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM risks WHERE policy_id = ? AND status = 'ACTIVE'",
            Integer.class,
            fixture.policyId()));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM period_risk_values "
                + "WHERE period_id = ? AND monthly_rent = 2500000.00 AND premium = 30000000.00",
            Integer.class,
            fixture.periodId()));
    assertOperationSucceeded(operationId);
  }

  @Test
  void cancelsRiskWithoutDeletingItOrItsHistoricalValue() {
    PolicyFixture fixture = createPolicyFixture(2);
    UUID operationId = UUID.randomUUID();
    insertContractOperation(operationId, "CANCEL_RISK", fixture.policyId(), fixture.periodId());
    OperationWork operation = claimOperation(operationId);

    transactionTemplate.executeWithoutResult(
        status ->
            operationProcessingRepository.completeRiskCancellation(
                operation,
                new RiskCancellationCommand(
                    fixture.policyId(), fixture.riskIds().getFirst(), fixture.periodId()),
                confirmed(operation, "CORE-RISK-CANCEL-001")));

    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM risks WHERE policy_id = ? AND status = 'ACTIVE'",
            Integer.class,
            fixture.policyId()));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM risks WHERE id = ? AND status = 'CANCELLED'",
            Integer.class,
            fixture.riskIds().getFirst()));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM period_risk_values WHERE period_id = ? AND risk_id = ?",
            Integer.class,
            fixture.periodId(),
            fixture.riskIds().getFirst()));
    assertOperationSucceeded(operationId);
  }

  @Test
  void cancelsPolicyAndAllActiveRisksAfterCoreConfirmation() {
    PolicyFixture fixture = createPolicyFixture(2);
    UUID operationId = UUID.randomUUID();
    insertContractOperation(operationId, "CANCEL_POLICY", fixture.policyId(), fixture.periodId());
    OperationWork operation = claimOperation(operationId);

    transactionTemplate.executeWithoutResult(
        status ->
            operationProcessingRepository.completePolicyCancellation(
                operation,
                new PolicyCancellationCommand(fixture.policyId(), fixture.periodId()),
                confirmed(operation, "CORE-POLICY-CANCEL-001")));

    assertEquals(
        "CANCELLED",
        jdbcTemplate.queryForObject(
            "SELECT status FROM policies WHERE id = ?", String.class, fixture.policyId()));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM risks WHERE policy_id = ? AND status = 'ACTIVE'",
            Integer.class,
            fixture.policyId()));
    assertEquals(
        2,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM risks WHERE policy_id = ? AND status = 'CANCELLED'",
            Integer.class,
            fixture.policyId()));
    assertEquals(
        "EXPIRED",
        jdbcTemplate.queryForObject(
            "SELECT status FROM policy_periods WHERE id = ?", String.class, fixture.periodId()));
    assertOperationSucceeded(operationId);
  }

  @Test
  void renewsPolicyByPersistingIpcSnapshotAndHistoricalPeriodValues() {
    PolicyFixture fixture = createPolicyFixture(2);
    UUID ipcRateId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO ipc_rates "
            + "(id, reference_period, effective_on, rate, source, is_fictional, created_at) "
            + "VALUES (?, ?, ?, ?, ?, TRUE, ?)",
        ipcRateId,
        "RENEW-" + UUID.randomUUID().toString().substring(0, 8),
        java.sql.Date.valueOf("2027-10-01"),
        new BigDecimal("0.08000"),
        "synthetic integration fixture",
        Timestamp.from(Instant.now()));
    UUID newPeriodId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    insertContractOperation(operationId, "RENEW_POLICY", fixture.policyId(), null);
    OperationWork operation = claimOperation(operationId);
    List<RenewedRiskValue> renewedValues =
        fixture.riskIds().stream()
            .map(
                riskId ->
                    new RenewedRiskValue(
                        riskId,
                        new BigDecimal("100000.00"),
                        new BigDecimal("108000.00"),
                        new BigDecimal("1296000.00"),
                        MoneyCurrency.COP))
            .toList();
    PolicyRenewalCommand request =
        new PolicyRenewalCommand(
            fixture.policyId(),
            fixture.periodId(),
            newPeriodId,
            java.time.LocalDate.parse("2027-10-30"),
            java.time.LocalDate.parse("2028-10-30"),
            12,
            ipcRateId,
            "RENEWAL-2027",
            new BigDecimal("0.08000"),
            java.time.LocalDate.parse("2027-10-01"),
            false,
            "demo-rent-v1",
            renewedValues);

    transactionTemplate.executeWithoutResult(
        status ->
            operationProcessingRepository.completePolicyRenewal(
                operation, request, confirmed(operation, "CORE-POLICY-RENEW-001")));

    assertEquals(
        "RENOVATED",
        jdbcTemplate.queryForObject(
            "SELECT status FROM policies WHERE id = ?", String.class, fixture.policyId()));
    assertEquals(
        "EXPIRED",
        jdbcTemplate.queryForObject(
            "SELECT status FROM policy_periods WHERE id = ?", String.class, fixture.periodId()));
    assertEquals(
        "CURRENT",
        jdbcTemplate.queryForObject(
            "SELECT status FROM policy_periods WHERE id = ?", String.class, newPeriodId));
    assertEquals(
        new BigDecimal("0.08000"),
        jdbcTemplate.queryForObject(
            "SELECT ipc_rate_snapshot FROM policy_periods WHERE id = ?",
            BigDecimal.class,
            newPeriodId));
    assertEquals(
        2,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM period_risk_values WHERE period_id = ? "
                + "AND monthly_rent = 108000.00 AND premium = 1296000.00",
            Integer.class,
            newPeriodId));
    assertEquals(
        newPeriodId,
        jdbcTemplate.queryForObject(
            "SELECT target_period_id FROM operations WHERE id = ?", UUID.class, operationId));
    assertOperationSucceeded(operationId);
  }

  private void insertParty(UUID id, String documentReference, String partyType) {
    jdbcTemplate.update(
        "INSERT INTO parties (id, party_type, display_name, document_reference, created_at) "
            + "VALUES (?, ?, ?, ?, ?)",
        id,
        partyType,
        "Synthetic integration party",
        documentReference,
        Timestamp.from(Instant.now()));
  }

  private void insertOperation(UUID id, String idempotencyKey) {
    jdbcTemplate.update(
        "INSERT INTO operations (id, consumer_scope, operation_type, idempotency_key, request_hash, "
            + "status, requested_payload, correlation_id, created_at, updated_at) "
            + "VALUES (?, 'integration-test', 'ISSUE_POLICY', ?, ?, 'PENDING', '{}'::jsonb, ?, ?, ?)",
        id,
        idempotencyKey,
        "a".repeat(64),
        UUID.randomUUID(),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
  }

  private void insertContractOperation(
      UUID id, String operationType, UUID policyId, UUID targetPeriodId) {
    Instant now = Instant.now();
    jdbcTemplate.update(
        "INSERT INTO operations "
            + "(id, consumer_scope, operation_type, idempotency_key, request_hash, policy_id, "
            + "target_period_id, status, requested_payload, correlation_id, created_at, updated_at) "
            + "VALUES (?, 'integration-test', ?, ?, ?, ?, ?, 'PENDING', '{}'::jsonb, ?, ?, ?)",
        id,
        operationType,
        "CONTRACT-" + id,
        "e".repeat(64),
        policyId,
        targetPeriodId,
        UUID.randomUUID(),
        Timestamp.from(now),
        Timestamp.from(now));
  }

  private OperationWork claimOperation(UUID operationId) {
    return transactionTemplate.execute(
        status ->
            operationProcessingRepository.claim(operationId, "contract-worker").orElseThrow());
  }

  private PolicyFixture createPolicyFixture(int riskCount) {
    UUID policyId = UUID.randomUUID();
    UUID holderId = UUID.randomUUID();
    UUID landlordId = UUID.randomUUID();
    insertParty(holderId, "FIXTURE-HOLDER-" + policyId, PartyType.PERSON.name());
    insertParty(landlordId, "FIXTURE-LANDLORD-" + policyId, PartyType.PERSON.name());
    jdbcTemplate.update(
        "INSERT INTO policies "
            + "(id, policy_number, policy_type, holder_party_id, status, created_at) "
            + "VALUES (?, ?, 'COLLECTIVE', ?, 'ACTIVE', ?)",
        policyId,
        "POL-" + policyId.toString().substring(0, 8),
        holderId,
        Timestamp.from(Instant.now()));
    UUID periodId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO policy_periods "
            + "(id, policy_id, sequence, starts_on, ends_on, duration_months, status, "
            + "calculation_version, core_reference) "
            + "VALUES (?, ?, 1, DATE '2026-10-30', DATE '2027-10-30', 12, 'CURRENT', ?, ?)",
        periodId,
        policyId,
        "demo-rent-v1",
        "CORE-FIXTURE-" + policyId.toString().substring(0, 8));
    java.util.ArrayList<UUID> riskIds = new java.util.ArrayList<>();
    for (int sequence = 1; sequence <= riskCount; sequence++) {
      UUID riskId = UUID.randomUUID();
      riskIds.add(riskId);
      jdbcTemplate.update(
          "INSERT INTO risks "
              + "(id, policy_id, risk_number, property_address, tenant_party_id, landlord_party_id, "
              + "status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)",
          riskId,
          policyId,
          "R" + sequence,
          "Synthetic fixture address " + sequence,
          holderId,
          landlordId,
          Timestamp.from(Instant.now()));
      jdbcTemplate.update(
          "INSERT INTO period_risk_values "
              + "(id, period_id, risk_id, monthly_rent, premium, currency) "
              + "VALUES (?, ?, ?, 100000.00, 1200000.00, 'COP')",
          UUID.randomUUID(),
          periodId,
          riskId);
    }
    return new PolicyFixture(policyId, periodId, holderId, landlordId, List.copyOf(riskIds));
  }

  private CoreOperationResult confirmed(OperationWork operation, String coreReference) {
    return new CoreOperationResult(
        operation.id(),
        operation.requestHash(),
        CoreOperationStatus.CONFIRMED,
        coreReference,
        "synthetic confirmation");
  }

  private void assertOperationSucceeded(UUID operationId) {
    assertEquals(
        "SUCCEEDED",
        jdbcTemplate.queryForObject(
            "SELECT status FROM operations WHERE id = ?", String.class, operationId));
  }

  private OperationDraft operationDraft(String idempotencyKey, String requestHash) {
    Instant now = Instant.now();
    return new OperationDraft(
        UUID.randomUUID(),
        "integration-test",
        "ISSUE_POLICY",
        idempotencyKey,
        requestHash,
        "{\"policy_type\":\"INDIVIDUAL\"}",
        UUID.randomUUID(),
        "test-operator",
        now,
        OperationStatus.PENDING,
        null,
        null,
        "POLICY_ISSUE_REQUESTED");
  }

  private int countRows(String table, UUID operationId) {
    String column =
        table.equals("operations")
            ? "id"
            : (table.equals("outbox_events") ? "operation_id" : "entity_id");
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
            Integer.class,
            operationId);
    return count == null ? 0 : count;
  }

  private static class ForcedRollback extends RuntimeException {}

  private record PolicyFixture(
      UUID policyId, UUID periodId, UUID holderId, UUID landlordId, List<UUID> riskIds) {}
}
