package com.example.policies.domain.error;

public class PolicyRuleViolationException extends RuntimeException {

  public PolicyRuleViolationException(String message) {
    super(message);
  }
}
