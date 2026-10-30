package com.example.policies.adapters.in.rest;

import com.example.policies.application.usecase.PolicyQueryService;
import com.example.policies.domain.error.PolicyRuleViolationException;
import com.example.policies.domain.model.Policy;
import com.example.policies.domain.model.PolicyStatus;
import com.example.policies.domain.model.PolicyType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpanishPoliciesController {

  private final PolicyQueryService policyQueryService;

  public SpanishPoliciesController(PolicyQueryService policyQueryService) {
    this.policyQueryService = policyQueryService;
  }

  @GetMapping("/polizas")
  @PreAuthorize("hasAnyRole('READ', 'OPERATOR', 'REVIEWER')")
  public List<Map<String, Object>> list(
      @RequestParam(required = false) String tipo, @RequestParam(required = false) String estado) {
    PolicyType policyType = parseType(tipo);
    PolicyStatus status = parseStatus(estado);
    return policyQueryService.list(policyType, status, 100, 0).items().stream()
        .map(SpanishPoliciesController::toSpanishPolicy)
        .toList();
  }

  private static PolicyType parseType(String type) {
    if (type == null || type.isBlank()) {
      return null;
    }
    return switch (type.toUpperCase()) {
      case "INDIVIDUAL" -> PolicyType.INDIVIDUAL;
      case "COLECTIVA", "COLLECTIVE" -> PolicyType.COLLECTIVE;
      default -> throw new PolicyRuleViolationException("Unknown policy type");
    };
  }

  private static PolicyStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    return switch (status.toUpperCase()) {
      case "ACTIVA", "ACTIVE" -> PolicyStatus.ACTIVE;
      case "RENOVADA", "RENOVATED" -> PolicyStatus.RENOVATED;
      case "CANCELADA", "CANCELLED" -> PolicyStatus.CANCELLED;
      default -> throw new PolicyRuleViolationException("Unknown policy status");
    };
  }

  private static Map<String, Object> toSpanishPolicy(Policy policy) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("polizaId", policy.id());
    response.put("numeroPoliza", policy.policyNumber());
    response.put("tipo", policy.policyType() == PolicyType.COLLECTIVE ? "COLECTIVA" : "INDIVIDUAL");
    response.put("estado", spanishStatus(policy.status()));
    response.put("tomadorPartyId", policy.holderPartyId());
    response.put("notas", policy.notes());
    return response;
  }

  private static String spanishStatus(PolicyStatus status) {
    return switch (status) {
      case ACTIVE -> "ACTIVA";
      case RENOVATED -> "RENOVADA";
      case CANCELLED -> "CANCELADA";
    };
  }
}
