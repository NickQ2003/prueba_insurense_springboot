package com.example.policies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.policies.domain.model.PartyType;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PostgresIntegrationIT {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.4-alpine3.20")
          .waitingFor(
              Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2)
                  .withStartupTimeout(Duration.ofMinutes(3)));

  @Autowired private MockMvc mockMvc;
  @Autowired private Flyway flyway;
  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void configureDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void exposesLivenessProbe() throws Exception {
    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void exposesReadinessProbe() throws Exception {
    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void appliesInitialFlywayMigrationAndCreatesDomainTables() {
    assertNotNull(flyway.info().current());
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
    insertParty(UUID.randomUUID(), "INTEGRATION-DOCUMENT-001", PartyType.PERSON.name());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> insertParty(UUID.randomUUID(), "INTEGRATION-DOCUMENT-001", PartyType.PERSON.name()));
  }

  @Test
  void rejectsInvalidPartyTypeAtDatabaseBoundary() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> insertParty(UUID.randomUUID(), "INTEGRATION-DOCUMENT-002", "INVALID"));
  }

  @Test
  void enforcesOperationIdempotencyAtDatabaseBoundary() {
    String idempotencyKey = "integration-idempotency-001";
    insertOperation(UUID.randomUUID(), idempotencyKey);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> insertOperation(UUID.randomUUID(), idempotencyKey));
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
