package com.example.policies.adapters.in.rest.dto;

import com.example.policies.application.usecase.PolicyWithRisks;
import com.example.policies.domain.model.PolicyStatus;
import com.example.policies.domain.model.PolicyType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PolicyDetailsResponse(
    UUID id,
    String policyNumber,
    PolicyType policyType,
    UUID holderPartyId,
    PolicyStatus status,
    String notes,
    Instant createdAt,
    List<RiskResponse> risks) {

  public static PolicyDetailsResponse from(PolicyWithRisks result) {
    var policy = result.policy();
    return new PolicyDetailsResponse(
        policy.id(),
        policy.policyNumber(),
        policy.policyType(),
        policy.holderPartyId(),
        policy.status(),
        policy.notes(),
        policy.createdAt(),
        result.risks().stream().map(RiskResponse::from).toList());
  }
}
