package com.example.policies.adapters.in.rest.dto;

import com.example.policies.domain.model.Risk;
import com.example.policies.domain.model.RiskStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RiskResponse(
    UUID id,
    UUID policyId,
    String riskNumber,
    String propertyAddress,
    UUID tenantPartyId,
    UUID landlordPartyId,
    RiskStatus status,
    Instant createdAt) {

  public static RiskResponse from(Risk risk) {
    return new RiskResponse(
        risk.id(),
        risk.policyId(),
        risk.riskNumber(),
        risk.propertyAddress(),
        risk.tenantPartyId(),
        risk.landlordPartyId(),
        risk.status(),
        risk.createdAt());
  }
}
