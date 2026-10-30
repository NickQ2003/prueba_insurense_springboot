package com.example.policies.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Risk(
    UUID id,
    UUID policyId,
    String riskNumber,
    String propertyAddress,
    UUID tenantPartyId,
    UUID landlordPartyId,
    RiskStatus status,
    Instant createdAt) {

  public Risk {
    if (id == null
        || policyId == null
        || tenantPartyId == null
        || landlordPartyId == null
        || status == null
        || createdAt == null) {
      throw new IllegalArgumentException(
          "Risk identity, parties, status and creation time are required");
    }
    if (riskNumber == null
        || riskNumber.isBlank()
        || propertyAddress == null
        || propertyAddress.isBlank()) {
      throw new IllegalArgumentException("Risk number and property address are required");
    }
  }
}
