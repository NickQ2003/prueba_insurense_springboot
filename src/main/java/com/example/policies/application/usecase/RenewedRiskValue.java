package com.example.policies.application.usecase;

import com.example.policies.domain.model.MoneyCurrency;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RenewedRiskValue(
    UUID riskId,
    BigDecimal previousMonthlyRent,
    BigDecimal monthlyRent,
    BigDecimal premium,
    MoneyCurrency currency) {}
