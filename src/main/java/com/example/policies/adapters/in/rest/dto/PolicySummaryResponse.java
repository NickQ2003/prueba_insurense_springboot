package com.example.policies.adapters.in.rest.dto;

import com.example.policies.domain.model.Policy;
import com.example.policies.domain.model.PolicyStatus;
import com.example.policies.domain.model.PolicyType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PolicySummaryResponse(
    UUID id,
    String policyNumber,
    PolicyType policyType,
    UUID holderPartyId,
    PolicyStatus status,
    String notes,
    Instant createdAt) {

  public static PolicySummaryResponse from(Policy policy) {
    return new PolicySummaryResponse(
        policy.id(),
        policy.policyNumber(),
        policy.policyType(),
        policy.holderPartyId(),
        policy.status(),
        policy.notes(),
        policy.createdAt());
  }
}
