package com.example.policies.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Party(
    UUID id, PartyType partyType, String displayName, String documentReference, Instant createdAt) {

  public Party {
    if (id == null || partyType == null || createdAt == null) {
      throw new IllegalArgumentException("Party identity, type and creation time are required");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("Party display name is required");
    }
    if (documentReference == null || documentReference.isBlank()) {
      throw new IllegalArgumentException("Party document reference is required");
    }
  }
}
