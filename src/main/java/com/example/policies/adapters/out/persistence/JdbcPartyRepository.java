package com.example.policies.adapters.out.persistence;

import com.example.policies.application.port.out.PartyRepository;
import com.example.policies.domain.model.Party;
import com.example.policies.domain.model.PartyType;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPartyRepository implements PartyRepository {

  private static final RowMapper<Party> PARTY_ROW_MAPPER =
      (resultSet, rowNumber) ->
          new Party(
              resultSet.getObject("id", UUID.class),
              PartyType.valueOf(resultSet.getString("party_type")),
              resultSet.getString("display_name"),
              resultSet.getString("document_reference"),
              resultSet.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public JdbcPartyRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Party save(Party party) {
    jdbcTemplate.update(
        "INSERT INTO parties (id, party_type, display_name, document_reference, created_at) "
            + "VALUES (?, ?, ?, ?, ?)",
        party.id(),
        party.partyType().name(),
        party.displayName(),
        party.documentReference(),
        Timestamp.from(party.createdAt()));
    return party;
  }

  @Override
  public Party saveIfAbsent(Party party) {
    List<Party> inserted =
        jdbcTemplate.query(
            "INSERT INTO parties (id, party_type, display_name, document_reference, created_at) "
                + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (document_reference) DO NOTHING RETURNING *",
            PARTY_ROW_MAPPER,
            party.id(),
            party.partyType().name(),
            party.displayName(),
            party.documentReference(),
            Timestamp.from(party.createdAt()));
    if (!inserted.isEmpty()) {
      return inserted.getFirst();
    }
    return jdbcTemplate
        .query(
            "SELECT * FROM parties WHERE document_reference = ?",
            PARTY_ROW_MAPPER,
            party.documentReference())
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("Seed party was not found after insert conflict"));
  }

  @Override
  public Optional<Party> findById(UUID id) {
    return jdbcTemplate.query("SELECT * FROM parties WHERE id = ?", PARTY_ROW_MAPPER, id).stream()
        .findFirst();
  }

  @Override
  public List<Party> findPage(PartyType partyType, String search, int limit, long offset) {
    FilterClause filter = buildFilter(partyType, search);
    List<Object> arguments = new ArrayList<>(filter.arguments());
    arguments.add(limit);
    arguments.add(offset);
    return jdbcTemplate.query(
        "SELECT * FROM parties" + filter.sql() + " ORDER BY display_name, id LIMIT ? OFFSET ?",
        PARTY_ROW_MAPPER,
        arguments.toArray());
  }

  @Override
  public long count(PartyType partyType, String search) {
    FilterClause filter = buildFilter(partyType, search);
    Long total =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM parties" + filter.sql(),
            Long.class,
            filter.arguments().toArray());
    return total == null ? 0 : total;
  }

  private FilterClause buildFilter(PartyType partyType, String search) {
    StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
    List<Object> arguments = new ArrayList<>();
    if (partyType != null) {
      sql.append(" AND party_type = ?");
      arguments.add(partyType.name());
    }
    if (search != null && !search.isBlank()) {
      sql.append(" AND (lower(display_name) LIKE ? OR lower(document_reference) LIKE ?)");
      String pattern = "%" + search.toLowerCase() + "%";
      arguments.add(pattern);
      arguments.add(pattern);
    }
    return new FilterClause(sql.toString(), arguments);
  }

  private record FilterClause(String sql, List<Object> arguments) {}
}
