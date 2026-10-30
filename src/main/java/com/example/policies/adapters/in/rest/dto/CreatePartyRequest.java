package com.example.policies.adapters.in.rest.dto;

import com.example.policies.domain.model.PartyType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreatePartyRequest(
    @NotNull PartyType partyType,
    @NotBlank @Size(max = 200) String displayName,
    @NotBlank @Size(max = 100) String documentReference) {}
