package com.example.policies.adapters.out.persistence;

import com.example.policies.application.port.out.PolicyReadRepository;
import com.example.policies.domain.model.IpcRateValue;
import com.example.policies.domain.model.Money;
import com.example.policies.domain.model.MoneyCurrency;
import com.example.policies.domain.model.PeriodRiskValue;
import com.example.policies.domain.model.PeriodStatus;
import com.example.policies.domain.model.Policy;
import com.example.policies.domain.model.PolicyPeriod;
import com.example.policies.domain.model.PolicyStatus;
import com.example.policies.domain.model.PolicyType;
import com.example.policies.domain.model.Risk;
import com.example.policies.domain.model.RiskStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

  private static final RowMapper<PolicyPeriod> PERIOD_ROW_MAPPER =
      (resultSet, rowNumber) -> {
        var rateSnapshot = resultSet.getBigDecimal("ipc_rate_snapshot");
        return new PolicyPeriod(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("policy_id", UUID.class),
            resultSet.getInt("sequence"),
            resultSet.getDate("starts_on").toLocalDate(),
            resultSet.getDate("ends_on").toLocalDate(),
            resultSet.getInt("duration_months"),
            PeriodStatus.valueOf(resultSet.getString("status")),
            resultSet.getObject("origin_period_id", UUID.class),
            resultSet.getObject("ipc_rate_id", UUID.class),
            rateSnapshot == null ? null : new IpcRateValue(rateSnapshot),
            resultSet.getString("calculation_version"),
            resultSet.getString("core_reference"));
      };

  private static final RowMapper<PeriodRiskValue> PERIOD_RISK_VALUE_ROW_MAPPER =
      (resultSet, rowNumber) ->
          new PeriodRiskValue(
              resultSet.getObject("id", UUID.class),
              resultSet.getObject("period_id", UUID.class),
              resultSet.getObject("risk_id", UUID.class),
              new Money(resultSet.getBigDecimal("monthly_rent")),
              new Money(resultSet.getBigDecimal("premium")),
              MoneyCurrency.valueOf(resultSet.getString("currency").trim()));

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
  public Optional<Policy> lockById(UUID id) {
    return jdbcTemplate
        .query("SELECT * FROM policies WHERE id = ? FOR UPDATE", POLICY_ROW_MAPPER, id)
        .stream()
        .findFirst();
  }

  @Override
  public List<Risk> findRisks(UUID policyId) {
    return jdbcTemplate.query(
        "SELECT * FROM risks WHERE policy_id = ? ORDER BY risk_number, id",
        RISK_ROW_MAPPER,
        policyId);
  }

  @Override
  public Optional<Risk> findRisk(UUID riskId) {
    return jdbcTemplate.query("SELECT * FROM risks WHERE id = ?", RISK_ROW_MAPPER, riskId).stream()
        .findFirst();
  }

  @Override
  public Optional<PolicyPeriod> findCurrentPeriod(UUID policyId) {
    return jdbcTemplate
        .query(
            "SELECT * FROM policy_periods WHERE policy_id = ? AND status = 'CURRENT' "
                + "ORDER BY sequence DESC LIMIT 1",
            PERIOD_ROW_MAPPER,
            policyId)
        .stream()
        .findFirst();
  }

  @Override
  public List<PolicyPeriod> findPeriods(UUID policyId) {
    return jdbcTemplate.query(
        "SELECT * FROM policy_periods WHERE policy_id = ? ORDER BY sequence",
        PERIOD_ROW_MAPPER,
        policyId);
  }

  @Override
  public List<PolicyPeriod> findCurrentPeriodsEndingBetween(LocalDate from, LocalDate through) {
    return jdbcTemplate.query(
        "SELECT pp.* FROM policy_periods pp "
            + "JOIN policies pol ON pol.id = pp.policy_id "
            + "WHERE pp.status = 'CURRENT' AND pol.status IN ('ACTIVE', 'RENOVATED') "
            + "AND pp.ends_on BETWEEN ? AND ? ORDER BY pp.ends_on, pp.policy_id",
        PERIOD_ROW_MAPPER,
        from,
        through);
  }

  @Override
  public List<PeriodRiskValue> findPeriodRiskValues(UUID periodId) {
    return jdbcTemplate.query(
        "SELECT * FROM period_risk_values WHERE period_id = ? ORDER BY risk_id",
        PERIOD_RISK_VALUE_ROW_MAPPER,
        periodId);
  }

  @Override
  public int countActiveRisks(UUID policyId) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM risks WHERE policy_id = ? AND status = 'ACTIVE'",
            Integer.class,
            policyId));
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
