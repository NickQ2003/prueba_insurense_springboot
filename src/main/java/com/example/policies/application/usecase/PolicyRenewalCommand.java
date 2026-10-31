package com.example.policies.application.usecase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PolicyRenewalCommand(
    UUID policyId,
    UUID sourcePeriodId,
    UUID newPeriodId,
    LocalDate startsOn,
    LocalDate endsOn,
    int durationMonths,
    UUID ipcRateId,
    String referencePeriod,
    BigDecimal ipcRateSnapshot,
    LocalDate effectiveOn,
    boolean rateRequired,
    String calculationVersion,
    @JsonProperty("risk_values") List<RenewedRiskValue> risks) {}
