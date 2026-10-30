package com.example.policies.adapters.in.rest.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InitialRiskRequest(
    @NotBlank @Size(max = 500) String propertyAddress,
    @NotNull UUID tenantPartyId,
    @NotNull UUID landlordPartyId,
    @NotBlank @Pattern(regexp = "^(?:0|[1-9][0-9]{0,11})(?:\\.[0-9]{1,2})?$") String monthlyRent) {}
