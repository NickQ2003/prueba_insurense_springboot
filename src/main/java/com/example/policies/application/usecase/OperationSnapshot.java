package com.example.policies.application.usecase;

import com.example.policies.domain.model.OperationStatus;
import java.time.Instant;
import java.util.UUID;

public record OperationSnapshot(
    UUID id,
    String operationType,
    String requestHash,
    OperationStatus status,
    int attemptCount,
    UUID correlationId,
    Instant createdAt,
    Instant updatedAt) {}
