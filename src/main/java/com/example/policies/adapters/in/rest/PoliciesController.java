package com.example.policies.adapters.in.rest;

import com.example.policies.adapters.in.rest.dto.PageResponse;
import com.example.policies.adapters.in.rest.dto.PolicyDetailsResponse;
import com.example.policies.adapters.in.rest.dto.PolicySummaryResponse;
import com.example.policies.application.error.ResourceNotFoundException;
import com.example.policies.application.usecase.PolicyQueryService;
import com.example.policies.domain.model.PolicyStatus;
import com.example.policies.domain.model.PolicyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
@Validated
public class PoliciesController {

  private final PolicyQueryService policyQueryService;

  public PoliciesController(PolicyQueryService policyQueryService) {
    this.policyQueryService = policyQueryService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('READ', 'OPERATOR', 'REVIEWER')")
  public PageResponse<PolicySummaryResponse> list(
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "0") @Min(0) long offset,
      @RequestParam(name = "policy_type", required = false) PolicyType policyType,
      @RequestParam(required = false) PolicyStatus status) {
    return PageResponse.from(
        policyQueryService.list(policyType, status, limit, offset), PolicySummaryResponse::from);
  }

  @GetMapping("/{policyId}")
  @PreAuthorize("hasAnyRole('READ', 'OPERATOR', 'REVIEWER')")
  public PolicyDetailsResponse get(@PathVariable UUID policyId) {
    return PolicyDetailsResponse.from(
        policyQueryService
            .findById(policyId)
            .orElseThrow(() -> new ResourceNotFoundException("Policy")));
  }
}
