package com.example.policies.adapters.in.rest.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateIpcRateRequest(
    @NotBlank @Size(max = 32) String referencePeriod,
    @NotNull LocalDate effectiveOn,
    @NotBlank @Pattern(regexp = "^(?:0|[1-9][0-9]{0,2})(?:\\.[0-9]{1,5})?$") String rate,
    @NotBlank @Size(max = 200) String source,
    boolean isFictional) {}
