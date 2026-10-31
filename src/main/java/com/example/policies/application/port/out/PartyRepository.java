package com.example.policies.application.port.out;

import com.example.policies.domain.model.Party;
import com.example.policies.domain.model.PartyType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyRepository {

  Party save(Party party);

  Party saveIfAbsent(Party party);

  Optional<Party> findById(UUID id);

  List<Party> findPage(PartyType partyType, String search, int limit, long offset);

  long count(PartyType partyType, String search);
}
