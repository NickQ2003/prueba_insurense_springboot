package com.example.policies.adapters.out.messaging;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OutboxEnvelope(UUID eventId, UUID operationId, String eventType, String payload) {}
