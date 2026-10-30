package com.example.policies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.policies.domain.model.PartyType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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

  @BeforeAll
  static void migrateSchema() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    flyway = Flyway.configure().dataSource(dataSource).load();
    flyway.migrate();
    jdbcTemplate = new JdbcTemplate(dataSource);
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
}
