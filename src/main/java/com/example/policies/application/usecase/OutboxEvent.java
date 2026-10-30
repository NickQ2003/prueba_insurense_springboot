package com.example.policies.application.usecase;

import java.util.UUID;

public record OutboxEvent(
    UUID id, UUID operationId, String eventType, String payload, int attemptCount) {}
