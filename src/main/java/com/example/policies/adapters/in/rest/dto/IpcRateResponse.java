package com.example.policies.adapters.in.rest.dto;

import com.example.policies.domain.model.IpcRate;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record IpcRateResponse(
    UUID id,
    String referencePeriod,
    LocalDate effectiveOn,
    String rate,
    String source,
    boolean isFictional,
    Instant createdAt) {

  public static IpcRateResponse from(IpcRate rate) {
    return new IpcRateResponse(
        rate.id(),
        rate.referencePeriod(),
        rate.effectiveOn(),
        rate.rate().value().toPlainString(),
        rate.source(),
        rate.fictional(),
        rate.createdAt());
  }
}
