package com.example.policies.application.usecase;

import java.util.UUID;

public record OperationDetails(
    OperationSnapshot operation, UUID policyId, UUID targetPeriodId, String requestedPayload) {}
