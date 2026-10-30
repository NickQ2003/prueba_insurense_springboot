package com.example.policies.application.usecase;

import com.example.policies.domain.model.PolicyType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record IssuePolicyCommand(
    PolicyType policyType,
    UUID holderPartyId,
    LocalDate startsOn,
    int durationMonths,
    List<IssueRiskCommand> risks,
    String notes) {}
