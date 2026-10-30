package com.example.policies.domain.service;

import com.example.policies.domain.model.IpcRateValue;
import com.example.policies.domain.model.Money;
import java.math.BigDecimal;
import java.util.Objects;

public final class RenewalCalculator {

  private RenewalCalculator() {}

  public static Money renewedMonthlyRent(Money previousRent, IpcRateValue rate) {
    Objects.requireNonNull(previousRent, "previousRent");
    Objects.requireNonNull(rate, "rate");
    BigDecimal multiplier = BigDecimal.ONE.add(rate.value());
    return new Money(previousRent.amount().multiply(multiplier));
  }

  public static Money premium(Money monthlyRent, int durationMonths) {
    Objects.requireNonNull(monthlyRent, "monthlyRent");
    if (durationMonths < 1) {
      throw new IllegalArgumentException("Duration must be positive");
    }
    return new Money(monthlyRent.amount().multiply(BigDecimal.valueOf(durationMonths)));
  }
}
