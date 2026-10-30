package com.example.policies.application.usecase;

import com.example.policies.application.error.OperationConflictException;
import com.example.policies.application.error.ResourceNotFoundException;
import com.example.policies.application.port.out.DurableOperationRepository;
import com.example.policies.application.port.out.PartyRepository;
import com.example.policies.domain.model.Money;
import com.example.policies.domain.service.PolicyRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationService {

  private static final String API_SCOPE = "api";
  private static final String ISSUE_POLICY = "ISSUE_POLICY";

  private final DurableOperationRepository operationRepository;
  private final PartyRepository partyRepository;
  private final ObjectMapper objectMapper;

  public OperationService(
      DurableOperationRepository operationRepository,
      PartyRepository partyRepository,
      ObjectMapper objectMapper) {
    this.operationRepository = operationRepository;
    this.partyRepository = partyRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public OperationSnapshot issuePolicy(
      IssuePolicyCommand request, String idempotencyKey, String actor) {
    requireParty(request.holderPartyId());
    List<UUID> tenants = request.risks().stream().map(IssueRiskCommand::tenantPartyId).toList();
    request
        .risks()
        .forEach(
            risk -> {
              requireParty(risk.tenantPartyId());
              requireParty(risk.landlordPartyId());
              Money.of(risk.monthlyRent());
            });
    PolicyRules.validateInitialRisks(request.policyType(), request.holderPartyId(), tenants);

    String payload = serialize(request);
    String requestHash = sha256(payload);
    Instant requestedAt = Instant.now();
    OperationDraft draft =
        new OperationDraft(
            UUID.randomUUID(),
            API_SCOPE,
            ISSUE_POLICY,
            idempotencyKey,
            requestHash,
            payload,
            UUID.randomUUID(),
            actor,
            requestedAt);
    return operationRepository.createIdempotently(draft);
  }

  @Transactional(readOnly = true)
  public OperationSnapshot findById(UUID id) {
    return operationRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Operation"));
  }

  @Transactional
  public OperationSnapshot reconcile(UUID id, String actor) {
    OperationSnapshot operation = findById(id);
    if (operation.status() != com.example.policies.domain.model.OperationStatus.NEEDS_REVIEW
        && operation.status() != com.example.policies.domain.model.OperationStatus.RECONCILING) {
      throw new OperationConflictException("Operation is not awaiting CORE reconciliation");
    }
    return operationRepository.requestReconciliation(id, actor);
  }

  private void requireParty(UUID partyId) {
    if (partyRepository.findById(partyId).isEmpty()) {
      throw new ResourceNotFoundException("Party");
    }
  }

  private String serialize(IssuePolicyCommand request) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("policy_type", request.policyType().name());
      payload.put("holder_party_id", request.holderPartyId());
      payload.put("starts_on", request.startsOn());
      payload.put("duration_months", request.durationMonths());
      payload.put(
          "risks",
          request.risks().stream()
              .map(
                  risk -> {
                    Map<String, Object> riskPayload = new LinkedHashMap<>();
                    riskPayload.put("property_address", risk.propertyAddress().trim());
                    riskPayload.put("tenant_party_id", risk.tenantPartyId());
                    riskPayload.put("landlord_party_id", risk.landlordPartyId());
                    riskPayload.put(
                        "monthly_rent", Money.of(risk.monthlyRent()).amount().toPlainString());
                    return riskPayload;
                  })
              .toList());
      if (request.notes() != null && !request.notes().isBlank()) {
        payload.put("notes", request.notes().trim());
      }
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not serialize policy request", exception);
    }
  }

  private String sha256(String payload) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
