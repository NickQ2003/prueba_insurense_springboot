package com.example.policies.entrypoints.scheduler;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.policies.application.port.out.PolicyReadRepository;
import com.example.policies.application.usecase.ContractOperationService;
import com.example.policies.application.usecase.OperationSnapshot;
import com.example.policies.domain.model.OperationStatus;
import com.example.policies.domain.model.PeriodStatus;
import com.example.policies.domain.model.PolicyPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RenewalSchedulerTest {

  @Mock private PolicyReadRepository policyReadRepository;
  @Mock private ContractOperationService contractOperationService;

  @Test
  void schedulesDuePeriodsWithAStableIdempotencyKey() {
    LocalDate today = LocalDate.parse("2026-10-01");
    UUID policyId = UUID.randomUUID();
    UUID periodId = UUID.randomUUID();
    PolicyPeriod period =
        new PolicyPeriod(
            periodId,
            policyId,
            1,
            today.minusMonths(11),
            today.plusDays(30),
            12,
            PeriodStatus.CURRENT,
            null,
            null,
            null,
            "demo-rent-v1",
            "CORE-SYNTHETIC");
    when(policyReadRepository.findCurrentPeriodsEndingBetween(today, today.plusDays(30)))
        .thenReturn(List.of(period));
    when(contractOperationService.renewPolicy(
            eq(policyId), isNull(), eq("scheduler-renewal-" + periodId), eq("system-scheduler")))
        .thenReturn(operationSnapshot());
    RenewalScheduler scheduler =
        new RenewalScheduler(policyReadRepository, contractOperationService, 30);

    scheduler.scheduleDueRenewals(today);
    scheduler.scheduleDueRenewals(today);

    verify(contractOperationService, times(2))
        .renewPolicy(
            eq(policyId), isNull(), eq("scheduler-renewal-" + periodId), eq("system-scheduler"));
  }

  private OperationSnapshot operationSnapshot() {
    Instant now = Instant.parse("2026-10-01T00:00:00Z");
    return new OperationSnapshot(
        UUID.randomUUID(),
        "RENEW_POLICY",
        "a".repeat(64),
        OperationStatus.WAITING_INPUT,
        0,
        UUID.randomUUID(),
        now,
        now);
  }
}
