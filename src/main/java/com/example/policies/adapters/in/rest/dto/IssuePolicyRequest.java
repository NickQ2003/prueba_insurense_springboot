package com.example.policies.adapters.in.rest.dto;

import com.example.policies.domain.model.PolicyType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record IssuePolicyRequest(
    @NotNull PolicyType policyType,
    @NotNull UUID holderPartyId,
    @NotNull LocalDate startsOn,
    @Min(1) @Max(32767) int durationMonths,
    @NotEmpty @Valid List<InitialRiskRequest> risks,
    @Size(max = 2000) String notes) {}
