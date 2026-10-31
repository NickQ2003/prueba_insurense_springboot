package com.example.policies.adapters.in.rest.dto;

import com.example.policies.domain.model.Risk;
import com.example.policies.domain.model.RiskStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record SpanishRiskResponse(
    @JsonProperty("riesgoId") UUID riskId,
    @JsonProperty("numeroRiesgo") String riskNumber,
    @JsonProperty("direccion") String propertyAddress,
    @JsonProperty("arrendatarioId") UUID tenantPartyId,
    @JsonProperty("arrendadorId") UUID landlordPartyId,
    @JsonProperty("estado") String status) {

  public static SpanishRiskResponse from(Risk risk) {
    return new SpanishRiskResponse(
        risk.id(),
        risk.riskNumber(),
        risk.propertyAddress(),
        risk.tenantPartyId(),
        risk.landlordPartyId(),
        risk.status() == RiskStatus.ACTIVE ? "ACTIVO" : "CANCELADO");
  }
}
