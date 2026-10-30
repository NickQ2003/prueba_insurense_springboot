package com.example.policies.adapters.in.rest.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApiErrorResponse(
    Instant timestamp, int status, String error, String message, Map<String, String> fields) {}
