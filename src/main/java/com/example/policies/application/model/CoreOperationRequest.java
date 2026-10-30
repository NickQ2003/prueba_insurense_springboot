package com.example.policies.application.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CoreOperationRequest(
    @NotNull UUID operationId,
    @NotBlank String operationType,
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String requestHash,
    @NotBlank String requestedPayload) {}
