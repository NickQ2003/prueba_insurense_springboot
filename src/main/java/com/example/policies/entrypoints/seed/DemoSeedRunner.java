package com.example.policies.entrypoints.seed;

import com.example.policies.application.port.out.DurableOperationRepository;
import com.example.policies.application.port.out.IpcRateRepository;
import com.example.policies.application.port.out.PartyRepository;
import com.example.policies.application.usecase.IssuePolicyCommand;
import com.example.policies.application.usecase.IssueRiskCommand;
import com.example.policies.application.usecase.OperationService;
import com.example.policies.domain.model.IpcRate;
import com.example.policies.domain.model.IpcRateValue;
import com.example.policies.domain.model.Party;
import com.example.policies.domain.model.PartyType;
import com.example.policies.domain.model.PolicyType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("seed")
public class DemoSeedRunner implements ApplicationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(DemoSeedRunner.class);
  private static final String ISSUE_IDEMPOTENCY_KEY = "DEMO-SEED-ISSUE-COLLECTIVE-001";
  private static final String IPC_REFERENCE_PERIOD = "DEMO-SEED-IPC";

  private final PartyRepository partyRepository;
  private final IpcRateRepository ipcRateRepository;
  private final DurableOperationRepository operationRepository;
  private final OperationService operationService;

  public DemoSeedRunner(
      PartyRepository partyRepository,
      IpcRateRepository ipcRateRepository,
      DurableOperationRepository operationRepository,
      OperationService operationService) {
    this.partyRepository = partyRepository;
    this.ipcRateRepository = ipcRateRepository;
    this.operationRepository = operationRepository;
    this.operationService = operationService;
  }

  @Override
  public void run(ApplicationArguments args) {
    Instant now = Instant.now();
    LocalDate today = LocalDate.now(Clock.systemUTC());
    Party holder =
        seedParty(PartyType.ORGANIZATION, "Synthetic demo holder", "DEMO-SEED-HOLDER-001", now);
    Party firstTenant =
        seedParty(PartyType.PERSON, "Synthetic tenant one", "DEMO-SEED-TENANT-001", now);
    Party secondTenant =
        seedParty(PartyType.PERSON, "Synthetic tenant two", "DEMO-SEED-TENANT-002", now);
    Party landlord =
        seedParty(PartyType.PERSON, "Synthetic landlord", "DEMO-SEED-LANDLORD-001", now);
    ipcRateRepository.saveIfAbsent(
        new IpcRate(
            UUID.randomUUID(),
            IPC_REFERENCE_PERIOD,
            today,
            IpcRateValue.of("0.08000"),
            "Synthetic demo IPC rate",
            true,
            now));

    var existingOperation =
        operationRepository.findByIdempotencyKey("api", "ISSUE_POLICY", ISSUE_IDEMPOTENCY_KEY);
    if (existingOperation.isPresent()) {
      LOGGER.info("Demo seed already created issue operation {}", existingOperation.get().id());
      return;
    }

    var operation =
        operationService.issuePolicy(
            new IssuePolicyCommand(
                PolicyType.COLLECTIVE,
                holder.id(),
                today,
                12,
                List.of(
                    new IssueRiskCommand(
                        "Synthetic demo property one",
                        firstTenant.id(),
                        landlord.id(),
                        "1000000.00"),
                    new IssueRiskCommand(
                        "Synthetic demo property two",
                        secondTenant.id(),
                        landlord.id(),
                        "1200000.00")),
                "Synthetic demo fixture"),
            ISSUE_IDEMPOTENCY_KEY,
            "demo-seed");
    LOGGER.info("Created synthetic demo issue operation {}", operation.id());
  }

  private Party seedParty(
      PartyType partyType, String displayName, String documentReference, Instant now) {
    return partyRepository.saveIfAbsent(
        new Party(UUID.randomUUID(), partyType, displayName, documentReference, now));
  }
}
