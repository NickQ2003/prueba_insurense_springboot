package com.example.policies.adapters.in.rest.dto;

import com.example.policies.domain.model.PeriodStatus;
import com.example.policies.domain.model.PolicyPeriod;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PolicyPeriodResponse(
    UUID id,
    int sequence,
    LocalDate startsOn,
    LocalDate endsOn,
    int durationMonths,
    PeriodStatus status,
    UUID ipcRateId,
    String ipcRateSnapshot,
    String calculationVersion,
    String coreReference) {

  public static PolicyPeriodResponse from(PolicyPeriod period) {
    return new PolicyPeriodResponse(
        period.id(),
        period.sequence(),
        period.startsOn(),
        period.endsOn(),
        period.durationMonths(),
        period.status(),
        period.ipcRateId(),
        period.ipcRateSnapshot() == null ? null : period.ipcRateSnapshot().value().toPlainString(),
        period.calculationVersion(),
        period.coreReference());
  }
}
