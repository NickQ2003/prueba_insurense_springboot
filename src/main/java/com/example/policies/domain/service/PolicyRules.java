package com.example.policies.domain.service;

import com.example.policies.domain.error.PolicyRuleViolationException;
import com.example.policies.domain.model.PolicyType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PolicyRules {

  private PolicyRules() {}

  public static void validateInitialRisks(
      PolicyType policyType, UUID holderPartyId, List<UUID> tenantPartyIds) {
    Objects.requireNonNull(policyType, "policyType");
    Objects.requireNonNull(holderPartyId, "holderPartyId");
    Objects.requireNonNull(tenantPartyIds, "tenantPartyIds");

    if (tenantPartyIds.isEmpty() || tenantPartyIds.stream().anyMatch(Objects::isNull)) {
      throw new PolicyRuleViolationException("A policy requires at least one risk with a tenant");
    }
    if (policyType == PolicyType.INDIVIDUAL) {
      if (tenantPartyIds.size() != 1) {
        throw new PolicyRuleViolationException("An individual policy must have exactly one risk");
      }
      if (!holderPartyId.equals(tenantPartyIds.getFirst())) {
        throw new PolicyRuleViolationException(
            "The holder and tenant must be the same for an individual policy");
      }
    }
  }

  public static void ensureCanAddRisk(PolicyType policyType) {
    if (Objects.requireNonNull(policyType, "policyType") == PolicyType.INDIVIDUAL) {
      throw new PolicyRuleViolationException("A risk cannot be added to an individual policy");
    }
  }

  public static void ensureRiskCanBeCancelled(PolicyType policyType, int activeRiskCount) {
    Objects.requireNonNull(policyType, "policyType");
    if (activeRiskCount < 1) {
      throw new PolicyRuleViolationException("The policy has no active risks");
    }
    if (policyType == PolicyType.INDIVIDUAL || activeRiskCount == 1) {
      throw new PolicyRuleViolationException(
          "The last active risk cannot be cancelled independently");
    }
  }
}
