package com.example.policies.domain.model;

import java.time.LocalDate;
import java.util.UUID;

public record PolicyPeriod(
    UUID id,
    UUID policyId,
    int sequence,
    LocalDate startsOn,
    LocalDate endsOn,
    int durationMonths,
    PeriodStatus status,
    UUID originPeriodId,
    UUID ipcRateId,
    IpcRateValue ipcRateSnapshot,
    String calculationVersion,
    String coreReference) {

  public PolicyPeriod {
    if (id == null || policyId == null || startsOn == null || endsOn == null || status == null) {
      throw new IllegalArgumentException("Policy period identity, dates and status are required");
    }
    if (sequence < 1 || durationMonths < 1 || !endsOn.isAfter(startsOn)) {
      throw new IllegalArgumentException(
          "Policy period sequence, duration and date range are invalid");
    }
    if (calculationVersion == null || calculationVersion.isBlank()) {
      throw new IllegalArgumentException("Policy period calculation version is required");
    }
  }

  public static LocalDate endDateExclusive(LocalDate startsOn, int durationMonths) {
    if (startsOn == null || durationMonths < 1) {
      throw new IllegalArgumentException("Start date and positive duration are required");
    }
    return startsOn.plusMonths(durationMonths);
  }
}
