package com.example.policies.adapters.out.persistence;

import com.example.policies.application.error.IdempotencyConflictException;
import com.example.policies.application.error.OperationConflictException;
import com.example.policies.application.error.ResourceNotFoundException;
import com.example.policies.application.port.out.DurableOperationRepository;
import com.example.policies.application.usecase.OperationDraft;
import com.example.policies.application.usecase.OperationSnapshot;
import com.example.policies.domain.model.OperationStatus;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcDurableOperationRepository implements DurableOperationRepository {

  private static final RowMapper<OperationSnapshot> OPERATION_ROW_MAPPER =
      (resultSet, rowNumber) ->
          new OperationSnapshot(
              resultSet.getObject("id", UUID.class),
              resultSet.getString("operation_type"),
              resultSet.getString("request_hash").trim(),
              OperationStatus.valueOf(resultSet.getString("status")),
              resultSet.getInt("attempt_count"),
              resultSet.getObject("correlation_id", UUID.class),
              resultSet.getTimestamp("created_at").toInstant(),
              resultSet.getTimestamp("updated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public JdbcDurableOperationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public OperationSnapshot createIdempotently(OperationDraft draft) {
    List<OperationSnapshot> inserted =
        jdbcTemplate.query(
            "INSERT INTO operations "
                + "(id, consumer_scope, operation_type, idempotency_key, request_hash, status, "
                + "requested_payload, correlation_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'PENDING', ?::jsonb, ?, ?, ?) "
                + "ON CONFLICT (consumer_scope, operation_type, idempotency_key) DO NOTHING "
                + "RETURNING id, operation_type, request_hash, status, attempt_count, "
                + "correlation_id, created_at, updated_at",
            OPERATION_ROW_MAPPER,
            draft.id(),
            draft.consumerScope(),
            draft.operationType(),
            draft.idempotencyKey(),
            draft.requestHash(),
            draft.requestedPayload(),
            draft.correlationId(),
            Timestamp.from(draft.createdAt()),
            Timestamp.from(draft.createdAt()));

    if (inserted.isEmpty()) {
      OperationSnapshot existing =
          jdbcTemplate
              .query(
                  "SELECT id, operation_type, request_hash, status, attempt_count, "
                      + "correlation_id, created_at, updated_at FROM operations "
                      + "WHERE consumer_scope = ? AND operation_type = ? AND idempotency_key = ?",
                  OPERATION_ROW_MAPPER,
                  draft.consumerScope(),
                  draft.operationType(),
                  draft.idempotencyKey())
              .stream()
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("Idempotent operation was not found"));
      if (!existing.requestHash().equals(draft.requestHash())) {
        throw new IdempotencyConflictException();
      }
      return existing;
    }

    insertOutboxEvent(draft);
    insertAuditEvent(draft);
    return inserted.getFirst();
  }

  @Override
  public Optional<OperationSnapshot> findById(UUID id) {
    return jdbcTemplate
        .query(
            "SELECT id, operation_type, request_hash, status, attempt_count, "
                + "correlation_id, created_at, updated_at FROM operations WHERE id = ?",
            OPERATION_ROW_MAPPER,
            id)
        .stream()
        .findFirst();
  }

  @Override
  @Transactional
  public OperationSnapshot requestReconciliation(UUID id, String actor) {
    int updated =
        jdbcTemplate.update(
            "UPDATE operations SET status = 'RECONCILING', next_attempt_at = CURRENT_TIMESTAMP, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND (status = 'NEEDS_REVIEW' "
                + "OR (status = 'RECONCILING' AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)))",
            id);
    if (updated != 1) {
      throw new OperationConflictException(
          "Operation is already processing or cannot be reconciled");
    }

    String payload = "{\"operation_id\":\"" + id + "\",\"operation_type\":\"RECONCILE\"}";
    jdbcTemplate.update(
        "INSERT INTO outbox_events "
            + "(id, operation_id, event_type, payload, available_at, created_at) "
            + "VALUES (?, ?, 'CORE_RECONCILIATION_REQUESTED', ?::jsonb, "
            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (operation_id, event_type) DO UPDATE SET "
            + "payload = EXCLUDED.payload, available_at = EXCLUDED.available_at, "
            + "lease_owner = NULL, lease_until = NULL, published_at = NULL",
        UUID.randomUUID(),
        id,
        payload);
    jdbcTemplate.update(
        "INSERT INTO audit_events "
            + "(id, actor, action, entity_type, entity_id, details, correlation_id, created_at) "
            + "SELECT ?, ?, 'RECONCILIATION_REQUESTED', 'OPERATION', id, "
            + "jsonb_build_object('operation_id', id), correlation_id, CURRENT_TIMESTAMP "
            + "FROM operations WHERE id = ?",
        UUID.randomUUID(),
        actor,
        id);
    return findById(id).orElseThrow(() -> new ResourceNotFoundException("Operation"));
  }

  private void insertOutboxEvent(OperationDraft draft) {
    String payload =
        "{\"operation_id\":\""
            + draft.id()
            + "\",\"operation_type\":\""
            + draft.operationType()
            + "\"}";
    jdbcTemplate.update(
        "INSERT INTO outbox_events "
            + "(id, operation_id, event_type, payload, available_at, created_at) "
            + "VALUES (?, ?, ?, ?::jsonb, ?, ?)",
        UUID.randomUUID(),
        draft.id(),
        "POLICY_ISSUE_REQUESTED",
        payload,
        Timestamp.from(draft.createdAt()),
        Timestamp.from(draft.createdAt()));
  }

  private void insertAuditEvent(OperationDraft draft) {
    String details = "{\"operation_type\":\"" + draft.operationType() + "\"}";
    jdbcTemplate.update(
        "INSERT INTO audit_events "
            + "(id, actor, action, entity_type, entity_id, details, correlation_id, created_at) "
            + "VALUES (?, ?, 'REQUESTED', 'OPERATION', ?, ?::jsonb, ?, ?)",
        UUID.randomUUID(),
        draft.actor(),
        draft.id(),
        details,
        draft.correlationId(),
        Timestamp.from(draft.createdAt()));
  }
}
