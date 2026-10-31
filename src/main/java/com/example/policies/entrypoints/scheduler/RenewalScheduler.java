package com.example.policies.entrypoints.scheduler;

import com.example.policies.application.error.OperationConflictException;
import com.example.policies.application.port.out.PolicyReadRepository;
import com.example.policies.application.usecase.ContractOperationService;
import com.example.policies.domain.model.PolicyPeriod;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("scheduler")
public class RenewalScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(RenewalScheduler.class);
  private static final String SYSTEM_ACTOR = "system-scheduler";

  private final PolicyReadRepository policyReadRepository;
  private final ContractOperationService contractOperationService;
  private final int renewalDaysAhead;

  public RenewalScheduler(
      PolicyReadRepository policyReadRepository,
      ContractOperationService contractOperationService,
      @Value("${renewal.days-ahead:30}") int renewalDaysAhead) {
    if (renewalDaysAhead < 0) {
      throw new IllegalArgumentException("Renewal days ahead cannot be negative");
    }
    this.policyReadRepository = policyReadRepository;
    this.contractOperationService = contractOperationService;
    this.renewalDaysAhead = renewalDaysAhead;
  }

  @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms:3600000}")
  public void scheduleUpcomingRenewals() {
    scheduleDueRenewals(LocalDate.now(Clock.systemUTC()));
  }

  void scheduleDueRenewals(LocalDate today) {
    LocalDate through = today.plusDays(renewalDaysAhead);
    for (PolicyPeriod period :
        policyReadRepository.findCurrentPeriodsEndingBetween(today, through)) {
      String idempotencyKey = "scheduler-renewal-" + period.id();
      try {
        var operation =
            contractOperationService.renewPolicy(
                period.policyId(), null, idempotencyKey, SYSTEM_ACTOR);
        LOGGER.info(
            "Scheduled renewal operation {} for policy {} with status {}",
            operation.id(),
            period.policyId(),
            operation.status());
      } catch (OperationConflictException exception) {
        LOGGER.info(
            "Skipped renewal scheduling for policy {} due to an active operation",
            period.policyId());
      } catch (RuntimeException exception) {
        LOGGER.error("Could not schedule renewal for policy {}", period.policyId(), exception);
      }
    }
  }
}
