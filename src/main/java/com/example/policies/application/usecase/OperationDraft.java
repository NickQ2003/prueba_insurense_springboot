package com.example.policies.application.usecase;

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
    Instant createdAt) {}
