package com.example.policies.adapters.in.rest.dto;

import com.example.policies.domain.model.Party;
import com.example.policies.domain.model.PartyType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PartyResponse(
    UUID id, PartyType partyType, String displayName, String documentReference, Instant createdAt) {

  public static PartyResponse from(Party party) {
    return new PartyResponse(
        party.id(),
        party.partyType(),
        party.displayName(),
        party.documentReference(),
        party.createdAt());
  }
}
