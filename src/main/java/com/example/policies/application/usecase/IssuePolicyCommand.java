package com.example.policies.application.usecase;

import com.example.policies.domain.model.PolicyType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record IssuePolicyCommand(
    PolicyType policyType,
    UUID holderPartyId,
    LocalDate startsOn,
    int durationMonths,
    List<IssueRiskCommand> risks,
    String notes) {}
