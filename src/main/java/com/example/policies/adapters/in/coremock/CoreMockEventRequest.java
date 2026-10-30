package com.example.policies.adapters.in.coremock;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CoreMockEventRequest(
    @JsonProperty("evento") @NotBlank String event,
    @JsonProperty("polizaId") @NotNull Long policyId) {}
