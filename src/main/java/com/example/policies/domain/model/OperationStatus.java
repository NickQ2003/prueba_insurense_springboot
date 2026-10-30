package com.example.policies.domain.model;

public enum OperationStatus {
  PENDING,
  WAITING_INPUT,
  PROCESSING,
  RECONCILING,
  SUCCEEDED,
  REJECTED,
  NEEDS_REVIEW
}
