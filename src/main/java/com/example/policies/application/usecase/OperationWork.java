package com.example.policies.application.usecase;

import com.example.policies.domain.model.OperationStatus;
import java.util.UUID;

public record OperationWork(
    UUID id,
    String operationType,
    String requestHash,
    String requestedPayload,
    UUID correlationId,
    OperationStatus status,
    int attemptCount,
    String leaseOwner) {}
