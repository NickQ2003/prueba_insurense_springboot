package com.example.policies.application.usecase;

import java.util.UUID;

public record AddRiskCommand(
    String propertyAddress, UUID tenantPartyId, UUID landlordPartyId, String monthlyRent) {}
