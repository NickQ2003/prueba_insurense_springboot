package com.example.policies.application.usecase;

import com.example.policies.application.port.out.PolicyReadRepository;
import com.example.policies.domain.model.Policy;
import com.example.policies.domain.model.PolicyStatus;
import com.example.policies.domain.model.PolicyType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyQueryService {

  private final PolicyReadRepository policyReadRepository;

  public PolicyQueryService(PolicyReadRepository policyReadRepository) {
    this.policyReadRepository = policyReadRepository;
  }

  @Transactional(readOnly = true)
  public PageResult<Policy> list(
      PolicyType policyType, PolicyStatus status, int limit, long offset) {
    return new PageResult<>(
        policyReadRepository.findPage(policyType, status, limit, offset),
        limit,
        offset,
        policyReadRepository.count(policyType, status));
  }

  @Transactional(readOnly = true)
  public Optional<PolicyWithRisks> findById(UUID id) {
    return policyReadRepository
        .findById(id)
        .map(policy -> new PolicyWithRisks(policy, policyReadRepository.findRisks(id)));
  }
}
