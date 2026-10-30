package com.example.policies.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record IpcRateValue(BigDecimal value) {

  public IpcRateValue {
    value = Objects.requireNonNull(value, "value").setScale(5, RoundingMode.HALF_UP);
    if (value.signum() < 0) {
      throw new IllegalArgumentException("IPC rate cannot be negative");
    }
    if (value.precision() > 8) {
      throw new IllegalArgumentException("IPC rate exceeds NUMERIC(8,5)");
    }
  }

  public static IpcRateValue of(String value) {
    return new IpcRateValue(new BigDecimal(value));
  }
}
