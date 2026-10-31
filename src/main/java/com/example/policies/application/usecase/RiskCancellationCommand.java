package com.example.policies.application.usecase;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RiskCancellationCommand(UUID policyId, UUID riskId, UUID periodId) {}
