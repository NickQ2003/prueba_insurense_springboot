package com.example.policies.application.port.out;

import com.example.policies.domain.model.PeriodRiskValue;
import com.example.policies.domain.model.Policy;
import com.example.policies.domain.model.PolicyPeriod;
import com.example.policies.domain.model.PolicyStatus;
import com.example.policies.domain.model.PolicyType;
import com.example.policies.domain.model.Risk;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyReadRepository {

  List<Policy> findPage(PolicyType policyType, PolicyStatus status, int limit, long offset);

  long count(PolicyType policyType, PolicyStatus status);

  Optional<Policy> findById(UUID id);

  Optional<Policy> lockById(UUID id);

  List<Risk> findRisks(UUID policyId);

  Optional<Risk> findRisk(UUID riskId);

  Optional<PolicyPeriod> findCurrentPeriod(UUID policyId);

  List<PolicyPeriod> findPeriods(UUID policyId);

  List<PeriodRiskValue> findPeriodRiskValues(UUID periodId);

  int countActiveRisks(UUID policyId);
}
