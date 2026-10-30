package com.example.policies.application.usecase;

import java.util.UUID;

public record IssueRiskCommand(
    String propertyAddress, UUID tenantPartyId, UUID landlordPartyId, String monthlyRent) {}
