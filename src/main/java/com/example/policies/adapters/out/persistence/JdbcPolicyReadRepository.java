package com.example.policies.adapters.out.persistence;

import com.example.policies.application.port.out.PolicyReadRepository;
import com.example.policies.domain.model.Policy;
import com.example.policies.domain.model.PolicyStatus;
import com.example.policies.domain.model.PolicyType;
import com.example.policies.domain.model.Risk;
import com.example.policies.domain.model.RiskStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPolicyReadRepository implements PolicyReadRepository {

  private static final RowMapper<Policy> POLICY_ROW_MAPPER =
      (resultSet, rowNumber) ->
          new Policy(
              resultSet.getObject("id", UUID.class),
              resultSet.getString("policy_number"),
              PolicyType.valueOf(resultSet.getString("policy_type")),
              resultSet.getObject("holder_party_id", UUID.class),
              PolicyStatus.valueOf(resultSet.getString("status")),
              resultSet.getString("notes"),
              resultSet.getTimestamp("created_at").toInstant());

  private static final RowMapper<Risk> RISK_ROW_MAPPER =
      (resultSet, rowNumber) ->
          new Risk(
              resultSet.getObject("id", UUID.class),
              resultSet.getObject("policy_id", UUID.class),
              resultSet.getString("risk_number"),
              resultSet.getString("property_address"),
              resultSet.getObject("tenant_party_id", UUID.class),
              resultSet.getObject("landlord_party_id", UUID.class),
              RiskStatus.valueOf(resultSet.getString("status")),
              resultSet.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public JdbcPolicyReadRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<Policy> findPage(PolicyType policyType, PolicyStatus status, int limit, long offset) {
    FilterClause filter = buildFilter(policyType, status);
    List<Object> arguments = new ArrayList<>(filter.arguments());
    arguments.add(limit);
    arguments.add(offset);
    return jdbcTemplate.query(
        "SELECT * FROM policies" + filter.sql() + " ORDER BY policy_number, id LIMIT ? OFFSET ?",
        POLICY_ROW_MAPPER,
        arguments.toArray());
  }

  @Override
  public long count(PolicyType policyType, PolicyStatus status) {
    FilterClause filter = buildFilter(policyType, status);
    Long total =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM policies" + filter.sql(),
            Long.class,
            filter.arguments().toArray());
    return total == null ? 0 : total;
  }

  @Override
  public Optional<Policy> findById(UUID id) {
    return jdbcTemplate.query("SELECT * FROM policies WHERE id = ?", POLICY_ROW_MAPPER, id).stream()
        .findFirst();
  }

  @Override
  public List<Risk> findRisks(UUID policyId) {
    return jdbcTemplate.query(
        "SELECT * FROM risks WHERE policy_id = ? ORDER BY risk_number, id",
        RISK_ROW_MAPPER,
        policyId);
  }

  private FilterClause buildFilter(PolicyType policyType, PolicyStatus status) {
    StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
    List<Object> arguments = new ArrayList<>();
    if (policyType != null) {
      sql.append(" AND policy_type = ?");
      arguments.add(policyType.name());
    }
    if (status != null) {
      sql.append(" AND status = ?");
      arguments.add(status.name());
    }
    return new FilterClause(sql.toString(), arguments);
  }

  private record FilterClause(String sql, List<Object> arguments) {}
}
