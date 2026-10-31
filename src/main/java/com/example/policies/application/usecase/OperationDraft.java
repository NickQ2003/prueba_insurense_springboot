package com.example.policies.application.usecase;

import com.example.policies.domain.model.OperationStatus;
import java.time.Instant;
import java.util.UUID;

public record OperationDraft(
    UUID id,
    String consumerScope,
    String operationType,
    String idempotencyKey,
    String requestHash,
    String requestedPayload,
    UUID correlationId,
    String actor,
    Instant createdAt,
    OperationStatus initialStatus,
    UUID policyId,
    UUID targetPeriodId,
    String outboxEventType) {}
