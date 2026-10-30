package com.example.policies.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount) {

  public Money {
    amount = Objects.requireNonNull(amount, "amount").setScale(2, RoundingMode.HALF_UP);
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("Money amount cannot be negative");
    }
    if (amount.precision() > 14) {
      throw new IllegalArgumentException("Money amount exceeds NUMERIC(14,2)");
    }
  }

  public static Money of(String amount) {
    return new Money(new BigDecimal(amount));
  }
}
