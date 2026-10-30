package com.example.policies.domain.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.policies.domain.error.PolicyRuleViolationException;
import com.example.policies.domain.model.PolicyType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PolicyRulesTest {

  private final UUID holderId = UUID.randomUUID();

  @Test
  void acceptsIndividualPolicyWithOneRiskForTheHolder() {
    assertDoesNotThrow(
        () -> PolicyRules.validateInitialRisks(PolicyType.INDIVIDUAL, holderId, List.of(holderId)));
  }

  @Test
  void rejectsIndividualPolicyWithMoreThanOneRisk() {
    assertThrows(
        PolicyRuleViolationException.class,
        () ->
            PolicyRules.validateInitialRisks(
                PolicyType.INDIVIDUAL, holderId, List.of(holderId, UUID.randomUUID())));
  }

  @Test
  void rejectsIndividualPolicyWhenHolderIsNotTenant() {
    assertThrows(
        PolicyRuleViolationException.class,
        () ->
            PolicyRules.validateInitialRisks(
                PolicyType.INDIVIDUAL, holderId, List.of(UUID.randomUUID())));
  }

  @Test
  void acceptsCollectivePolicyWithMultipleRisks() {
    assertDoesNotThrow(
        () ->
            PolicyRules.validateInitialRisks(
                PolicyType.COLLECTIVE, holderId, List.of(UUID.randomUUID(), UUID.randomUUID())));
  }

  @Test
  void rejectsCollectivePolicyWithoutRisks() {
    assertThrows(
        PolicyRuleViolationException.class,
        () -> PolicyRules.validateInitialRisks(PolicyType.COLLECTIVE, holderId, List.of()));
  }

  @Test
  void preventsAddingRisksToAnIndividualPolicy() {
    assertThrows(
        PolicyRuleViolationException.class,
        () -> PolicyRules.ensureCanAddRisk(PolicyType.INDIVIDUAL));
  }

  @Test
  void onlyAllowsCancellingCollectiveRiskWhenAnotherRemainsActive() {
    assertDoesNotThrow(() -> PolicyRules.ensureRiskCanBeCancelled(PolicyType.COLLECTIVE, 2));
    assertThrows(
        PolicyRuleViolationException.class,
        () -> PolicyRules.ensureRiskCanBeCancelled(PolicyType.COLLECTIVE, 1));
    assertThrows(
        PolicyRuleViolationException.class,
        () -> PolicyRules.ensureRiskCanBeCancelled(PolicyType.INDIVIDUAL, 1));
  }
}
