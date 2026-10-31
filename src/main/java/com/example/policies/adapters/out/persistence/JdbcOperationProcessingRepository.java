package com.example.policies.adapters.out.persistence;

import com.example.policies.application.model.CoreOperationResult;
import com.example.policies.application.port.out.OperationProcessingRepository;
import com.example.policies.application.usecase.IssuePolicyCommand;
import com.example.policies.application.usecase.IssueRiskCommand;
import com.example.policies.application.usecase.OperationWork;
import com.example.policies.application.usecase.PolicyCancellationCommand;
import com.example.policies.application.usecase.PolicyRenewalCommand;
import com.example.policies.application.usecase.RenewedRiskValue;
import com.example.policies.application.usecase.RiskCancellationCommand;
import com.example.policies.application.usecase.RiskCreationCommand;
import com.example.policies.domain.model.Money;
import com.example.policies.domain.model.MoneyCurrency;
import com.example.policies.domain.model.OperationStatus;
import com.example.policies.domain.model.PeriodStatus;
import com.example.policies.domain.model.PolicyStatus;
import com.example.policies.domain.model.RiskStatus;
import com.example.policies.domain.service.RenewalCalculator;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcOperationProcessingRepository implements OperationProcessingRepository {

  private static final RowMapper<OperationWork> OPERATION_WORK_ROW_MAPPER =
      (resultSet, rowNumber) ->
          new OperationWork(
              resultSet.getObject("id", UUID.class),
              resultSet.getString("operation_type"),
              resultSet.getString("request_hash").trim(),
              resultSet.getString("requested_payload"),
              resultSet.getObject("correlation_id", UUID.class),
              OperationStatus.valueOf(resultSet.getString("status")),
              resultSet.getInt("attempt_count"),
              resultSet.getString("lease_owner"));

  private final JdbcTemplate jdbcTemplate;

  public JdbcOperationProcessingRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public Optional<OperationWork> claim(UUID operationId, String leaseOwner) {
    List<OperationWork> claimed =
        jdbcTemplate.query(
            "UPDATE operations SET status = CASE WHEN status = 'PENDING' "
                + "THEN 'PROCESSING' ELSE 'RECONCILING' END, "
                + "attempt_count = attempt_count + 1, lease_owner = ?, "
                + "lease_until = CURRENT_TIMESTAMP + INTERVAL '60 seconds', "
                + "updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND (status IN ('PENDING', 'RECONCILING') "
                + "OR (status = 'PROCESSING' AND lease_until < CURRENT_TIMESTAMP)) "
                + "RETURNING id, operation_type, request_hash, requested_payload::text AS requested_payload, "
                + "correlation_id, status, attempt_count, lease_owner",
            OPERATION_WORK_ROW_MAPPER,
            leaseOwner,
            operationId);
    return claimed.stream().findFirst();
  }

  @Override
  @Transactional
  public void scheduleReconciliation(OperationWork operation, String errorCode) {
    if (!updateState(operation, OperationStatus.RECONCILING, errorCode, null, null)) {
      return;
    }
    String payload =
        "{\"operation_id\":\""
            + operation.id()
            + "\",\"operation_type\":\""
            + operation.operationType()
            + "\"}";
    jdbcTemplate.update(
        "INSERT INTO outbox_events "
            + "(id, operation_id, event_type, payload, available_at, created_at) "
            + "VALUES (?, ?, 'CORE_RECONCILIATION_REQUESTED', ?::jsonb, "
            + "CURRENT_TIMESTAMP + INTERVAL '5 seconds', CURRENT_TIMESTAMP) "
            + "ON CONFLICT (operation_id, event_type) DO NOTHING",
        UUID.randomUUID(),
        operation.id(),
        payload);
    insertAudit(operation, "CORE_RECONCILIATION_REQUESTED", errorCode);
  }

  @Override
  @Transactional
  public void markNeedsReview(OperationWork operation, String errorCode) {
    if (updateState(operation, OperationStatus.NEEDS_REVIEW, errorCode, null, null)) {
      insertAudit(operation, "CORE_RESULT_NEEDS_REVIEW", errorCode);
    }
  }

  @Override
  @Transactional
  public void markRejected(OperationWork operation, CoreOperationResult coreResult) {
    String result = "{\"status\":\"REJECTED\",\"message\":\"CORE rejected the request\"}";
    if (updateState(operation, OperationStatus.REJECTED, null, null, result)) {
      insertAudit(operation, "CONTRACT_CHANGE_REJECTED", "CORE_REJECTED");
    }
  }

  @Override
  @Transactional
  public void completePolicyIssue(
      OperationWork operation, IssuePolicyCommand request, CoreOperationResult coreResult) {
    if (!"ISSUE_POLICY".equals(operation.operationType())) {
      throw new IllegalArgumentException("Unsupported operation type");
    }

    Instant now = Instant.now();
    UUID policyId = UUID.randomUUID();
    UUID periodId = UUID.randomUUID();
    String policyNumber =
        "POL-" + operation.id().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    LocalDate endsOn = request.startsOn().plusMonths(request.durationMonths());

    jdbcTemplate.update(
        "INSERT INTO policies "
            + "(id, policy_number, policy_type, holder_party_id, status, notes, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
        policyId,
        policyNumber,
        request.policyType().name(),
        request.holderPartyId(),
        PolicyStatus.ACTIVE.name(),
        request.notes(),
        Timestamp.from(now));

    jdbcTemplate.update(
        "INSERT INTO policy_periods "
            + "(id, policy_id, sequence, starts_on, ends_on, duration_months, status, "
            + "calculation_version, core_reference) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?)",
        periodId,
        policyId,
        request.startsOn(),
        endsOn,
        request.durationMonths(),
        PeriodStatus.CURRENT.name(),
        "demo-rent-v1",
        coreResult.coreReference());

    int riskSequence = 0;
    for (IssueRiskCommand risk : request.risks()) {
      riskSequence++;
      UUID riskId = UUID.randomUUID();
      Money monthlyRent = Money.of(risk.monthlyRent());
      Money premium = RenewalCalculator.premium(monthlyRent, request.durationMonths());
      jdbcTemplate.update(
          "INSERT INTO risks "
              + "(id, policy_id, risk_number, property_address, tenant_party_id, "
              + "landlord_party_id, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
          riskId,
          policyId,
          "R" + riskSequence,
          risk.propertyAddress().trim(),
          risk.tenantPartyId(),
          risk.landlordPartyId(),
          RiskStatus.ACTIVE.name(),
          Timestamp.from(now));
      jdbcTemplate.update(
          "INSERT INTO period_risk_values "
              + "(id, period_id, risk_id, monthly_rent, premium, currency) "
              + "VALUES (?, ?, ?, ?, ?, ?)",
          UUID.randomUUID(),
          periodId,
          riskId,
          monthlyRent.amount(),
          premium.amount(),
          MoneyCurrency.COP.name());
    }

    String resultPayload =
        "{\"policy_id\":\""
            + policyId
            + "\",\"policy_number\":\""
            + policyNumber
            + "\",\"core_reference\":\""
            + coreResult.coreReference()
            + "\"}";
    int updated =
        jdbcTemplate.update(
            "UPDATE operations SET status = 'SUCCEEDED', policy_id = ?, target_period_id = ?, "
                + "result_payload = ?::jsonb, lease_owner = NULL, lease_until = NULL, "
                + "next_attempt_at = NULL, updated_at = ? "
                + "WHERE id = ? AND lease_owner = ? AND status IN ('PROCESSING', 'RECONCILING')",
            policyId,
            periodId,
            resultPayload,
            Timestamp.from(now),
            operation.id(),
            operation.leaseOwner());
    if (updated != 1) {
      throw new IllegalStateException("Operation lease was lost before local completion");
    }
    insertAudit(operation, "POLICY_ISSUED", policyNumber);
    insertNotificationDelivery(operation, policyId, now);
  }

  @Override
  @Transactional
  public void completeRiskAddition(
      OperationWork operation, RiskCreationCommand request, CoreOperationResult coreResult) {
    requireOperationType(operation, "ADD_RISK");
    Integer durationMonths =
        jdbcTemplate
            .query(
                "SELECT duration_months FROM policy_periods "
                    + "WHERE id = ? AND policy_id = ? AND status = 'CURRENT'",
                (resultSet, rowNumber) -> resultSet.getInt(1),
                request.periodId(),
                request.policyId())
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Current policy period was not found"));
    Integer sequence =
        jdbcTemplate.queryForObject(
            "SELECT count(*) + 1 FROM risks WHERE policy_id = ?",
            Integer.class,
            request.policyId());
    UUID riskId = UUID.randomUUID();
    Money monthlyRent = Money.of(request.monthlyRent());
    Money premium = RenewalCalculator.premium(monthlyRent, durationMonths);
    jdbcTemplate.update(
        "INSERT INTO risks "
            + "(id, policy_id, risk_number, property_address, tenant_party_id, landlord_party_id, "
            + "status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)",
        riskId,
        request.policyId(),
        "R" + sequence,
        request.propertyAddress().trim(),
        request.tenantPartyId(),
        request.landlordPartyId());
    jdbcTemplate.update(
        "INSERT INTO period_risk_values (id, period_id, risk_id, monthly_rent, premium, currency) "
            + "VALUES (?, ?, ?, ?, ?, 'COP')",
        UUID.randomUUID(),
        request.periodId(),
        riskId,
        monthlyRent.amount(),
        premium.amount());

    completeContractOperation(
        operation,
        request.policyId(),
        "{\"risk_id\":\""
            + riskId
            + "\",\"core_reference\":"
            + jsonValue(coreResult.coreReference())
            + "}",
        "RISK_ADDED",
        riskId.toString(),
        request.periodId(),
        request.policyId(),
        "RISK_CHANGE_NOTIFICATION");
  }

  @Override
  @Transactional
  public void completeRiskCancellation(
      OperationWork operation, RiskCancellationCommand request, CoreOperationResult coreResult) {
    requireOperationType(operation, "CANCEL_RISK");
    int updatedRisk =
        jdbcTemplate.update(
            "UPDATE risks SET status = 'CANCELLED' "
                + "WHERE id = ? AND policy_id = ? AND status = 'ACTIVE'",
            request.riskId(),
            request.policyId());
    if (updatedRisk != 1) {
      throw new IllegalStateException("Active risk was not found during cancellation");
    }
    completeContractOperation(
        operation,
        request.policyId(),
        "{\"risk_id\":\""
            + request.riskId()
            + "\",\"core_reference\":"
            + jsonValue(coreResult.coreReference())
            + "}",
        "RISK_CANCELLED",
        request.riskId().toString(),
        request.periodId(),
        request.policyId(),
        "RISK_CHANGE_NOTIFICATION");
  }

  @Override
  @Transactional
  public void completePolicyCancellation(
      OperationWork operation, PolicyCancellationCommand request, CoreOperationResult coreResult) {
    requireOperationType(operation, "CANCEL_POLICY");
    int cancelledPolicy =
        jdbcTemplate.update(
            "UPDATE policies SET status = 'CANCELLED' "
                + "WHERE id = ? AND status IN ('ACTIVE', 'RENOVATED')",
            request.policyId());
    if (cancelledPolicy != 1) {
      throw new IllegalStateException("Policy is not active during cancellation");
    }
    jdbcTemplate.update(
        "UPDATE risks SET status = 'CANCELLED' WHERE policy_id = ? AND status = 'ACTIVE'",
        request.policyId());
    jdbcTemplate.update(
        "UPDATE policy_periods SET status = 'EXPIRED' "
            + "WHERE policy_id = ? AND status = 'CURRENT'",
        request.policyId());
    completeContractOperation(
        operation,
        request.policyId(),
        "{\"policy_id\":\""
            + request.policyId()
            + "\",\"core_reference\":"
            + jsonValue(coreResult.coreReference())
            + "}",
        "POLICY_CANCELLED",
        request.policyId().toString(),
        request.periodId(),
        request.policyId(),
        "POLICY_CHANGE_NOTIFICATION");
  }

  @Override
  @Transactional
  public void completePolicyRenewal(
      OperationWork operation, PolicyRenewalCommand request, CoreOperationResult coreResult) {
    requireOperationType(operation, "RENEW_POLICY");
    if (request.rateRequired() || request.ipcRateId() == null || request.risks().isEmpty()) {
      throw new IllegalStateException("Renewal is missing its IPC rate or risk snapshots");
    }
    int expiredSourcePeriod =
        jdbcTemplate.update(
            "UPDATE policy_periods SET status = 'EXPIRED' "
                + "WHERE id = ? AND policy_id = ? AND status = 'CURRENT'",
            request.sourcePeriodId(),
            request.policyId());
    if (expiredSourcePeriod != 1) {
      throw new IllegalStateException("Source policy period is no longer current");
    }
    Integer sequence =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(sequence), 0) + 1 FROM policy_periods WHERE policy_id = ?",
            Integer.class,
            request.policyId());
    jdbcTemplate.update(
        "INSERT INTO policy_periods "
            + "(id, policy_id, sequence, starts_on, ends_on, duration_months, status, "
            + "origin_period_id, ipc_rate_id, ipc_rate_snapshot, calculation_version, core_reference) "
            + "VALUES (?, ?, ?, ?, ?, ?, 'CURRENT', ?, ?, ?, ?, ?)",
        request.newPeriodId(),
        request.policyId(),
        sequence,
        request.startsOn(),
        request.endsOn(),
        request.durationMonths(),
        request.sourcePeriodId(),
        request.ipcRateId(),
        request.ipcRateSnapshot(),
        request.calculationVersion(),
        coreResult.coreReference());
    for (RenewedRiskValue risk : request.risks()) {
      jdbcTemplate.update(
          "INSERT INTO period_risk_values "
              + "(id, period_id, risk_id, monthly_rent, premium, currency) VALUES (?, ?, ?, ?, ?, ?)",
          UUID.randomUUID(),
          request.newPeriodId(),
          risk.riskId(),
          risk.monthlyRent(),
          risk.premium(),
          risk.currency().name());
    }
    int updatedPolicy =
        jdbcTemplate.update(
            "UPDATE policies SET status = 'RENOVATED' WHERE id = ? AND status IN ('ACTIVE', 'RENOVATED')",
            request.policyId());
    if (updatedPolicy != 1) {
      throw new IllegalStateException("Policy is not active during renewal");
    }
    completeContractOperation(
        operation,
        request.policyId(),
        "{\"period_id\":\""
            + request.newPeriodId()
            + "\",\"core_reference\":"
            + jsonValue(coreResult.coreReference())
            + "}",
        "POLICY_RENEWED",
        request.newPeriodId().toString(),
        request.newPeriodId(),
        request.policyId(),
        "POLICY_CHANGE_NOTIFICATION");
  }

  private void requireOperationType(OperationWork operation, String expectedType) {
    if (!expectedType.equals(operation.operationType())) {
      throw new IllegalArgumentException("Unsupported operation type");
    }
  }

  private void completeContractOperation(
      OperationWork operation,
      UUID policyId,
      String resultPayload,
      String auditAction,
      String auditCode,
      UUID targetPeriodId,
      UUID notificationPolicyId,
      String notificationEventType) {
    int updated =
        jdbcTemplate.update(
            "UPDATE operations SET status = 'SUCCEEDED', policy_id = COALESCE(?, policy_id), "
                + "target_period_id = COALESCE(?, target_period_id), result_payload = ?::jsonb, "
                + "lease_owner = NULL, lease_until = NULL, next_attempt_at = NULL, "
                + "updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND lease_owner = ? AND status IN ('PROCESSING', 'RECONCILING')",
            policyId,
            targetPeriodId,
            resultPayload,
            operation.id(),
            operation.leaseOwner());
    if (updated != 1) {
      throw new IllegalStateException("Operation lease was lost before local completion");
    }
    insertAudit(operation, auditAction, auditCode);
    insertNotificationDelivery(
        operation, notificationPolicyId, Instant.now(), notificationEventType);
  }

  private String jsonValue(String value) {
    if (value == null) {
      return "null";
    }
    String escaped =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    return "\"" + escaped + "\"";
  }

  private boolean updateState(
      OperationWork operation,
      OperationStatus status,
      String errorCode,
      UUID policyId,
      String resultPayload) {
    return jdbcTemplate.update(
            "UPDATE operations SET status = ?, last_error_code = ?, policy_id = COALESCE(?, policy_id), "
                + "result_payload = COALESCE(?::jsonb, result_payload), lease_owner = NULL, "
                + "lease_until = NULL, updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND lease_owner = ? AND status IN ('PROCESSING', 'RECONCILING')",
            status.name(),
            errorCode,
            policyId,
            resultPayload,
            operation.id(),
            operation.leaseOwner())
        == 1;
  }

  private void insertAudit(OperationWork operation, String action, String code) {
    String details = "{\"operation_id\":\"" + operation.id() + "\",\"code\":\"" + code + "\"}";
    jdbcTemplate.update(
        "INSERT INTO audit_events "
            + "(id, actor, action, entity_type, entity_id, details, correlation_id, created_at) "
            + "VALUES (?, 'system-worker', ?, 'OPERATION', ?, ?::jsonb, ?, CURRENT_TIMESTAMP)",
        UUID.randomUUID(),
        action,
        operation.id(),
        details,
        operation.correlationId());
  }

  private void insertNotificationDelivery(OperationWork operation, UUID policyId, Instant now) {
    insertNotificationDelivery(operation, policyId, now, "POLICY_ISSUED_NOTIFICATION");
  }

  private void insertNotificationDelivery(
      OperationWork operation, UUID policyId, Instant now, String eventType) {
    UUID outboxEventId = UUID.randomUUID();
    String payload =
        "{\"operation_id\":\"" + operation.id() + "\",\"policy_id\":\"" + policyId + "\"}";
    jdbcTemplate.update(
        "INSERT INTO outbox_events "
            + "(id, operation_id, event_type, payload, available_at, created_at) "
            + "VALUES (?, ?, ?, ?::jsonb, ?, ?)",
        outboxEventId,
        operation.id(),
        eventType,
        payload,
        Timestamp.from(now),
        Timestamp.from(now));
    jdbcTemplate.update(
        "INSERT INTO notification_deliveries (id, outbox_event_id, channel, recipient, delivered_at) "
            + "VALUES (?, ?, 'EMAIL', 'demo-operator@example.invalid', ?)",
        UUID.randomUUID(),
        outboxEventId,
        Timestamp.from(now));
  }
}
