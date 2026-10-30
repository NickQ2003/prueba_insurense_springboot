package com.example.policies.domain.model;

import java.util.UUID;

public record PeriodRiskValue(
    UUID id, UUID periodId, UUID riskId, Money monthlyRent, Money premium, MoneyCurrency currency) {

  public PeriodRiskValue {
    if (id == null
        || periodId == null
        || riskId == null
        || monthlyRent == null
        || premium == null
        || currency == null) {
      throw new IllegalArgumentException("Period risk value fields are required");
    }
  }
}
