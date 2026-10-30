package com.example.policies.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record IpcRate(
    UUID id,
    String referencePeriod,
    LocalDate effectiveOn,
    IpcRateValue rate,
    String source,
    boolean fictional,
    Instant createdAt) {

  public IpcRate {
    if (id == null || effectiveOn == null || rate == null || createdAt == null) {
      throw new IllegalArgumentException(
          "IPC rate identity, effective date, value and creation time are required");
    }
    if (referencePeriod == null
        || referencePeriod.isBlank()
        || source == null
        || source.isBlank()) {
      throw new IllegalArgumentException("IPC reference period and source are required");
    }
  }
}
