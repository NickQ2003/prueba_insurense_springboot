package com.example.policies.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Policy(
    UUID id,
    String policyNumber,
    PolicyType policyType,
    UUID holderPartyId,
    PolicyStatus status,
    String notes,
    Instant createdAt) {

  public Policy {
    if (id == null
        || policyType == null
        || holderPartyId == null
        || status == null
        || createdAt == null) {
      throw new IllegalArgumentException(
          "Policy identity, type, holder, status and creation time are required");
    }
    if (policyNumber == null || policyNumber.isBlank()) {
      throw new IllegalArgumentException("Policy number is required");
    }
  }
}
