package com.example.policies.application.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CoreOperationResult(
    UUID operationId,
    String requestHash,
    CoreOperationStatus status,
    String coreReference,
    String message) {}
