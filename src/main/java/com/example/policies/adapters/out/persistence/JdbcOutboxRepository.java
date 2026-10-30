package com.example.policies.adapters.out.persistence;

import com.example.policies.application.port.out.OutboxRepository;
import com.example.policies.application.usecase.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcOutboxRepository implements OutboxRepository {

  private static final RowMapper<OutboxEvent> OUTBOX_ROW_MAPPER =
      (resultSet, rowNumber) ->
          new OutboxEvent(
              resultSet.getObject("id", UUID.class),
              resultSet.getObject("operation_id", UUID.class),
              resultSet.getString("event_type"),
              resultSet.getString("payload"),
              resultSet.getInt("attempt_count"));

  private final JdbcTemplate jdbcTemplate;

  public JdbcOutboxRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public List<OutboxEvent> claimReadyBatch(int limit, String leaseOwner) {
    return jdbcTemplate.query(
        "WITH ready AS ("
            + " SELECT id FROM outbox_events"
            + " WHERE published_at IS NULL AND available_at <= CURRENT_TIMESTAMP"
            + "   AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)"
            + " ORDER BY available_at, created_at LIMIT ? FOR UPDATE SKIP LOCKED"
            + ") UPDATE outbox_events AS event"
            + " SET lease_owner = ?, lease_until = CURRENT_TIMESTAMP + INTERVAL '30 seconds',"
            + "     attempt_count = event.attempt_count + 1"
            + " FROM ready WHERE event.id = ready.id"
            + " RETURNING event.id, event.operation_id, event.event_type, "
            + "event.payload::text AS payload, event.attempt_count",
        OUTBOX_ROW_MAPPER,
        limit,
        leaseOwner);
  }

  @Override
  @Transactional
  public boolean markPublished(OutboxEvent event, String leaseOwner) {
    return jdbcTemplate.update(
            "UPDATE outbox_events SET published_at = CURRENT_TIMESTAMP, "
                + "lease_owner = NULL, lease_until = NULL "
                + "WHERE id = ? AND lease_owner = ? AND published_at IS NULL",
            event.id(),
            leaseOwner)
        == 1;
  }

  @Override
  @Transactional
  public void releaseForRetry(OutboxEvent event, String leaseOwner, int delaySeconds) {
    jdbcTemplate.update(
        "UPDATE outbox_events SET available_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'), "
            + "lease_owner = NULL, lease_until = NULL "
            + "WHERE id = ? AND lease_owner = ? AND published_at IS NULL",
        delaySeconds,
        event.id(),
        leaseOwner);
  }
}
