package com.example.policies.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PolicyPeriodTest {

  @Test
  void calculatesExclusiveEndDateUsingCalendarMonths() {
    assertEquals(
        LocalDate.parse("2025-02-28"),
        PolicyPeriod.endDateExclusive(LocalDate.parse("2024-02-29"), 12));
  }

  @Test
  void rejectsNonPositiveDuration() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PolicyPeriod.endDateExclusive(LocalDate.parse("2024-01-01"), 0));
  }
}
