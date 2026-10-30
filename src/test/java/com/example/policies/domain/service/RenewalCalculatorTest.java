package com.example.policies.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.policies.domain.model.IpcRateValue;
import com.example.policies.domain.model.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RenewalCalculatorTest {

  @Test
  void appliesIpcAndRoundsRenewedRentToTwoDecimalsHalfUp() {
    Money renewedRent =
        RenewalCalculator.renewedMonthlyRent(Money.of("1.00"), IpcRateValue.of("0.00500"));

    assertEquals(new BigDecimal("1.01"), renewedRent.amount());
  }

  @Test
  void calculatesAnnualPremiumFromMonthlyRent() {
    Money premium = RenewalCalculator.premium(Money.of("1500000.00"), 12);

    assertEquals(new BigDecimal("18000000.00"), premium.amount());
  }

  @Test
  void rejectsZeroMonthDuration() {
    assertThrows(
        IllegalArgumentException.class, () -> RenewalCalculator.premium(Money.of("100.00"), 0));
  }

  @Test
  void rejectsNegativeMoneyAndIpcRates() {
    assertThrows(IllegalArgumentException.class, () -> Money.of("-0.01"));
    assertThrows(IllegalArgumentException.class, () -> IpcRateValue.of("-0.01000"));
  }

  @Test
  void rejectsAmountsOutsideDatabasePrecision() {
    assertThrows(IllegalArgumentException.class, () -> Money.of("1000000000000.00"));
    assertThrows(IllegalArgumentException.class, () -> IpcRateValue.of("1000.00000"));
  }
}
