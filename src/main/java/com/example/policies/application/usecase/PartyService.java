package com.example.policies.application.usecase;

import com.example.policies.application.port.out.PartyRepository;
import com.example.policies.domain.model.Party;
import com.example.policies.domain.model.PartyType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartyService {

  private final PartyRepository partyRepository;

  public PartyService(PartyRepository partyRepository) {
    this.partyRepository = partyRepository;
  }

  @Transactional
  public Party create(PartyType partyType, String displayName, String documentReference) {
    Party party =
        new Party(
            UUID.randomUUID(),
            partyType,
            displayName.trim(),
            documentReference.trim(),
            Instant.now());
    return partyRepository.save(party);
  }

  @Transactional(readOnly = true)
  public Optional<Party> findById(UUID id) {
    return partyRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public PageResult<Party> list(PartyType partyType, String search, int limit, long offset) {
    String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
    return new PageResult<>(
        partyRepository.findPage(partyType, normalizedSearch, limit, offset),
        limit,
        offset,
        partyRepository.count(partyType, normalizedSearch));
  }
}
