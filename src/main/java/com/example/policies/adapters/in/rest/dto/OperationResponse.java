package com.example.policies.adapters.in.rest.dto;

import com.example.policies.application.usecase.OperationSnapshot;
import com.example.policies.domain.model.OperationStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OperationResponse(
    UUID id,
    String operationType,
    OperationStatus status,
    int attemptCount,
    UUID correlationId,
    Instant createdAt,
    Instant updatedAt) {

  public static OperationResponse from(OperationSnapshot operation) {
    return new OperationResponse(
        operation.id(),
        operation.operationType(),
        operation.status(),
        operation.attemptCount(),
        operation.correlationId(),
        operation.createdAt(),
        operation.updatedAt());
  }
}
