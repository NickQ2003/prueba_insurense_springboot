package com.example.policies.adapters.out.persistence;

import com.example.policies.application.port.out.IpcRateRepository;
import com.example.policies.domain.model.IpcRate;
import com.example.policies.domain.model.IpcRateValue;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcIpcRateRepository implements IpcRateRepository {

  private static final RowMapper<IpcRate> IPC_RATE_ROW_MAPPER =
      (resultSet, rowNumber) ->
          new IpcRate(
              resultSet.getObject("id", UUID.class),
              resultSet.getString("reference_period"),
              resultSet.getDate("effective_on").toLocalDate(),
              new IpcRateValue(resultSet.getBigDecimal("rate")),
              resultSet.getString("source"),
              resultSet.getBoolean("is_fictional"),
              resultSet.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public JdbcIpcRateRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public IpcRate save(IpcRate rate) {
    jdbcTemplate.update(
        "INSERT INTO ipc_rates "
            + "(id, reference_period, effective_on, rate, source, is_fictional, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
        rate.id(),
        rate.referencePeriod(),
        rate.effectiveOn(),
        rate.rate().value(),
        rate.source(),
        rate.fictional(),
        Timestamp.from(rate.createdAt()));
    return rate;
  }

  @Override
  public Optional<IpcRate> findById(UUID id) {
    return jdbcTemplate
        .query("SELECT * FROM ipc_rates WHERE id = ?", IPC_RATE_ROW_MAPPER, id)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<IpcRate> findLatestApplicable(java.time.LocalDate effectiveOn) {
    return jdbcTemplate
        .query(
            "SELECT * FROM ipc_rates WHERE effective_on <= ? "
                + "ORDER BY effective_on DESC, created_at DESC, id DESC LIMIT 1",
            IPC_RATE_ROW_MAPPER,
            effectiveOn)
        .stream()
        .findFirst();
  }

  @Override
  public List<IpcRate> findPage(int limit, long offset) {
    return jdbcTemplate.query(
        "SELECT * FROM ipc_rates ORDER BY effective_on DESC, reference_period, id LIMIT ? OFFSET ?",
        IPC_RATE_ROW_MAPPER,
        limit,
        offset);
  }

  @Override
  public long count() {
    Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM ipc_rates", Long.class);
    return total == null ? 0 : total;
  }
}
