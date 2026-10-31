package com.example.policies.application.usecase;

import com.example.policies.application.error.IdempotencyConflictException;
import com.example.policies.application.error.OperationConflictException;
import com.example.policies.application.error.ResourceNotFoundException;
import com.example.policies.application.port.out.DurableOperationRepository;
import com.example.policies.application.port.out.IpcRateRepository;
import com.example.policies.application.port.out.PartyRepository;
import com.example.policies.application.port.out.PolicyReadRepository;
import com.example.policies.domain.model.IpcRate;
import com.example.policies.domain.model.Money;
import com.example.policies.domain.model.OperationStatus;
import com.example.policies.domain.model.PeriodRiskValue;
import com.example.policies.domain.model.Policy;
import com.example.policies.domain.model.PolicyPeriod;
import com.example.policies.domain.model.PolicyStatus;
import com.example.policies.domain.model.Risk;
import com.example.policies.domain.model.RiskStatus;
import com.example.policies.domain.service.PolicyRules;
import com.example.policies.domain.service.RenewalCalculator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractOperationService {

  private static final String API_SCOPE = "api";
  private static final String RENEWAL_FORMULA_VERSION = "demo-rent-v1";

  private final PolicyReadRepository policyRepository;
  private final PartyRepository partyRepository;
  private final IpcRateRepository ipcRateRepository;
  private final DurableOperationRepository operationRepository;
  private final ObjectMapper objectMapper;

  public ContractOperationService(
      PolicyReadRepository policyRepository,
      PartyRepository partyRepository,
      IpcRateRepository ipcRateRepository,
      DurableOperationRepository operationRepository,
      ObjectMapper objectMapper) {
    this.policyRepository = policyRepository;
    this.partyRepository = partyRepository;
    this.ipcRateRepository = ipcRateRepository;
    this.operationRepository = operationRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public OperationSnapshot addRisk(
      UUID policyId, AddRiskCommand command, String idempotencyKey, String actor) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("policy_id", policyId);
    request.put("property_address", command.propertyAddress().trim());
    request.put("tenant_party_id", command.tenantPartyId());
    request.put("landlord_party_id", command.landlordPartyId());
    request.put("monthly_rent", Money.of(command.monthlyRent()).amount().toPlainString());
    String requestHash = hash(serialize(request));
    Policy policy = lockPolicy(policyId);
    OperationSnapshot replay = findReplay("ADD_RISK", idempotencyKey, requestHash);
    if (replay != null) {
      return replay;
    }
    requireNoOtherOperation(policyId);
    ensurePolicyCanChange(policy);
    PolicyRules.ensureCanAddRisk(policy.policyType());
    PolicyPeriod period = currentPeriod(policyId);
    if (!LocalDate.now(Clock.systemUTC()).equals(period.startsOn())) {
      throw new OperationConflictException(
          "Risks can only be added at the start of a policy period");
    }
    requireParty(command.tenantPartyId());
    requireParty(command.landlordPartyId());
    request.put("period_id", period.id());
    return createOperation(
        "ADD_RISK",
        idempotencyKey,
        requestHash,
        request,
        actor,
        OperationStatus.PENDING,
        policyId,
        period.id(),
        "RISK_ADD_REQUESTED");
  }

  @Transactional
  public OperationSnapshot cancelRisk(
      UUID policyId, UUID riskId, String idempotencyKey, String actor) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("policy_id", policyId);
    request.put("risk_id", riskId);
    String requestHash = hash(serialize(request));
    Policy policy = lockPolicy(policyId);
    OperationSnapshot replay = findReplay("CANCEL_RISK", idempotencyKey, requestHash);
    if (replay != null) {
      return replay;
    }
    requireNoOtherOperation(policyId);
    ensurePolicyCanChange(policy);
    Risk risk =
        policyRepository
            .findRisk(riskId)
            .filter(candidate -> candidate.policyId().equals(policyId))
            .orElseThrow(() -> new ResourceNotFoundException("Risk"));
    if (risk.status() != RiskStatus.ACTIVE) {
      throw new OperationConflictException("Risk is already cancelled");
    }
    PolicyRules.ensureRiskCanBeCancelled(
        policy.policyType(), policyRepository.countActiveRisks(policyId));
    PolicyPeriod period = currentPeriod(policyId);
    request.put("period_id", period.id());
    return createOperation(
        "CANCEL_RISK",
        idempotencyKey,
        requestHash,
        request,
        actor,
        OperationStatus.PENDING,
        policyId,
        period.id(),
        "RISK_CANCELLATION_REQUESTED");
  }

  @Transactional
  public OperationSnapshot cancelRiskById(UUID riskId, String idempotencyKey, String actor) {
    Risk risk =
        policyRepository.findRisk(riskId).orElseThrow(() -> new ResourceNotFoundException("Risk"));
    return cancelRisk(risk.policyId(), riskId, idempotencyKey, actor);
  }

  @Transactional
  public OperationSnapshot cancelPolicy(UUID policyId, String idempotencyKey, String actor) {
    Map<String, Object> request = Map.of("policy_id", policyId);
    String requestHash = hash(serialize(request));
    Policy policy = lockPolicy(policyId);
    OperationSnapshot replay = findReplay("CANCEL_POLICY", idempotencyKey, requestHash);
    if (replay != null) {
      return replay;
    }
    requireNoOtherOperation(policyId);
    ensurePolicyCanChange(policy);
    PolicyPeriod period = currentPeriod(policyId);
    request = new LinkedHashMap<>(request);
    request.put("period_id", period.id());
    return createOperation(
        "CANCEL_POLICY",
        idempotencyKey,
        requestHash,
        request,
        actor,
        OperationStatus.PENDING,
        policyId,
        period.id(),
        "POLICY_CANCELLATION_REQUESTED");
  }

  @Transactional
  public OperationSnapshot renewPolicy(
      UUID policyId, UUID ipcRateId, String idempotencyKey, String actor) {
    Map<String, Object> idempotencyRequest = new LinkedHashMap<>();
    idempotencyRequest.put("policy_id", policyId);
    idempotencyRequest.put("ipc_rate_id", ipcRateId);
    String requestHash = hash(serialize(idempotencyRequest));
    Policy policy = lockPolicy(policyId);
    OperationSnapshot replay = findReplay("RENEW_POLICY", idempotencyKey, requestHash);
    if (replay != null) {
      return replay;
    }
    requireNoOtherOperation(policyId);
    ensurePolicyCanChange(policy);
    PolicyPeriod current = currentPeriod(policyId);
    LocalDate startsOn = current.endsOn();
    Optional<IpcRate> applicableRate = resolveRate(ipcRateId, startsOn);
    UUID targetPeriodId = UUID.randomUUID();
    Map<String, Object> snapshot =
        renewalSnapshot(policyId, current, targetPeriodId, applicableRate);
    return createOperation(
        "RENEW_POLICY",
        idempotencyKey,
        requestHash,
        snapshot,
        actor,
        applicableRate.isPresent() ? OperationStatus.PENDING : OperationStatus.WAITING_INPUT,
        policyId,
        null,
        applicableRate.isPresent() ? "POLICY_RENEWAL_REQUESTED" : null);
  }

  @Transactional
  public OperationSnapshot resumeRenewal(UUID operationId, UUID ipcRateId, String actor) {
    OperationDetails details =
        operationRepository
            .findDetailsById(operationId)
            .orElseThrow(() -> new ResourceNotFoundException("Operation"));
    if (!"RENEW_POLICY".equals(details.operation().operationType())
        || details.operation().status() != OperationStatus.WAITING_INPUT) {
      throw new OperationConflictException("Operation is not waiting for an IPC rate");
    }
    UUID policyId = details.policyId();
    Policy policy = lockPolicy(policyId);
    ensurePolicyCanChange(policy);
    if (operationRepository.hasOtherInFlightOperation(policyId, operationId)) {
      throw new OperationConflictException("Another contract operation is in progress");
    }

    JsonNode previousPayload = parse(details.requestedPayload());
    UUID sourcePeriodId = UUID.fromString(previousPayload.get("source_period_id").asText());
    UUID targetPeriodId = UUID.fromString(previousPayload.get("new_period_id").asText());
    int durationMonths = previousPayload.get("duration_months").asInt();
    PolicyPeriod sourcePeriod = currentPeriod(policyId);
    if (!sourcePeriod.id().equals(sourcePeriodId)) {
      throw new OperationConflictException("The source period changed before renewal resumed");
    }
    IpcRate rate =
        resolveRate(ipcRateId, sourcePeriod.endsOn())
            .orElseThrow(() -> new ResourceNotFoundException("Applicable IPC rate"));
    Map<String, Object> snapshot =
        buildRenewalSnapshot(
            policyId, sourcePeriod, targetPeriodId, durationMonths, Optional.of(rate));
    return operationRepository.resumeWaitingInput(operationId, serialize(snapshot), actor);
  }

  private Map<String, Object> renewalSnapshot(
      UUID policyId, PolicyPeriod current, UUID targetPeriodId, Optional<IpcRate> rate) {
    return buildRenewalSnapshot(policyId, current, targetPeriodId, current.durationMonths(), rate);
  }

  private Map<String, Object> buildRenewalSnapshot(
      UUID policyId,
      PolicyPeriod current,
      UUID targetPeriodId,
      int durationMonths,
      Optional<IpcRate> rate) {
    LocalDate startsOn = current.endsOn();
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("policy_id", policyId);
    snapshot.put("source_period_id", current.id());
    snapshot.put("new_period_id", targetPeriodId);
    snapshot.put("starts_on", startsOn);
    snapshot.put("ends_on", PolicyPeriod.endDateExclusive(startsOn, durationMonths));
    snapshot.put("duration_months", durationMonths);
    snapshot.put("calculation_version", RENEWAL_FORMULA_VERSION);
    if (rate.isEmpty()) {
      snapshot.put("rate_required", true);
      snapshot.put("risk_values", List.of());
      return snapshot;
    }

    IpcRate ipcRate = rate.get();
    snapshot.put("rate_required", false);
    snapshot.put("ipc_rate_id", ipcRate.id());
    snapshot.put("reference_period", ipcRate.referencePeriod());
    snapshot.put("ipc_rate_snapshot", ipcRate.rate().value().toPlainString());
    snapshot.put("effective_on", ipcRate.effectiveOn());
    Set<UUID> activeRiskIds =
        policyRepository.findRisks(policyId).stream()
            .filter(risk -> risk.status() == RiskStatus.ACTIVE)
            .map(Risk::id)
            .collect(Collectors.toSet());
    List<Map<String, Object>> values = new ArrayList<>();
    for (PeriodRiskValue oldValue : policyRepository.findPeriodRiskValues(current.id())) {
      if (!activeRiskIds.contains(oldValue.riskId())) {
        continue;
      }
      Money newRent = RenewalCalculator.renewedMonthlyRent(oldValue.monthlyRent(), ipcRate.rate());
      Money newPremium = RenewalCalculator.premium(newRent, durationMonths);
      Map<String, Object> value = new LinkedHashMap<>();
      value.put("risk_id", oldValue.riskId());
      value.put("previous_monthly_rent", oldValue.monthlyRent().amount().toPlainString());
      value.put("monthly_rent", newRent.amount().toPlainString());
      value.put("premium", newPremium.amount().toPlainString());
      value.put("currency", oldValue.currency().name());
      values.add(value);
    }
    if (values.size() != activeRiskIds.size()) {
      throw new OperationConflictException("Current period is missing an active risk value");
    }
    snapshot.put("risk_values", values);
    return snapshot;
  }

  private Optional<IpcRate> resolveRate(UUID ipcRateId, LocalDate startsOn) {
    if (ipcRateId == null) {
      return ipcRateRepository.findLatestApplicable(startsOn);
    }
    IpcRate rate =
        ipcRateRepository
            .findById(ipcRateId)
            .orElseThrow(() -> new ResourceNotFoundException("IPC rate"));
    if (rate.effectiveOn().isAfter(startsOn)) {
      throw new OperationConflictException("IPC rate is not applicable to the renewal date");
    }
    return Optional.of(rate);
  }

  private OperationSnapshot findReplay(String operationType, String key, String requestHash) {
    Optional<OperationSnapshot> existing =
        operationRepository.findByIdempotencyKey(API_SCOPE, operationType, key);
    if (existing.isEmpty()) {
      return null;
    }
    if (!existing.get().requestHash().equals(requestHash)) {
      throw new IdempotencyConflictException();
    }
    return existing.get();
  }

  private Policy lockPolicy(UUID policyId) {
    return policyRepository
        .lockById(policyId)
        .orElseThrow(() -> new ResourceNotFoundException("Policy"));
  }

  private PolicyPeriod currentPeriod(UUID policyId) {
    return policyRepository
        .findCurrentPeriod(policyId)
        .orElseThrow(() -> new OperationConflictException("Policy has no current period"));
  }

  private void ensurePolicyCanChange(Policy policy) {
    if (policy.status() == PolicyStatus.CANCELLED) {
      throw new OperationConflictException("Cancelled policy cannot be changed");
    }
  }

  private void requireNoOtherOperation(UUID policyId) {
    if (operationRepository.hasInFlightOperation(policyId)) {
      throw new OperationConflictException("Another contract operation is in progress");
    }
  }

  private void requireParty(UUID partyId) {
    if (partyRepository.findById(partyId).isEmpty()) {
      throw new ResourceNotFoundException("Party");
    }
  }

  private OperationSnapshot createOperation(
      String operationType,
      String idempotencyKey,
      String requestHash,
      Map<String, Object> payload,
      String actor,
      OperationStatus initialStatus,
      UUID policyId,
      UUID targetPeriodId,
      String outboxEventType) {
    Instant now = Instant.now();
    return operationRepository.createIdempotently(
        new OperationDraft(
            UUID.randomUUID(),
            API_SCOPE,
            operationType,
            idempotencyKey,
            requestHash,
            serialize(payload),
            UUID.randomUUID(),
            actor,
            now,
            initialStatus,
            policyId,
            targetPeriodId,
            outboxEventType));
  }

  private String serialize(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not serialize contract operation", exception);
    }
  }

  private String hash(String payload) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private JsonNode parse(String payload) {
    try {
      return objectMapper.readTree(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored operation payload is invalid", exception);
    }
  }
}
