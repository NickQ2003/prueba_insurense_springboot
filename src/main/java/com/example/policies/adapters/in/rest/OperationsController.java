package com.example.policies.adapters.in.rest;

import com.example.policies.adapters.in.rest.dto.IssuePolicyRequest;
import com.example.policies.adapters.in.rest.dto.OperationResponse;
import com.example.policies.adapters.in.rest.dto.ResumeRenewalRequest;
import com.example.policies.application.usecase.ContractOperationService;
import com.example.policies.application.usecase.IssuePolicyCommand;
import com.example.policies.application.usecase.IssueRiskCommand;
import com.example.policies.application.usecase.OperationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
@Validated
public class OperationsController {

  private final OperationService operationService;
  private final ContractOperationService contractOperationService;

  public OperationsController(
      OperationService operationService, ContractOperationService contractOperationService) {
    this.operationService = operationService;
    this.contractOperationService = contractOperationService;
  }

  @PostMapping("/policies")
  @PreAuthorize("hasRole('OPERATOR')")
  public ResponseEntity<OperationResponse> issuePolicy(
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
      @Valid @RequestBody IssuePolicyRequest request,
      Authentication authentication) {
    OperationResponse response =
        OperationResponse.from(
            operationService.issuePolicy(
                toCommand(request), idempotencyKey, authentication.getName()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/operations/{operationId}")
            .buildAndExpand(response.id())
            .toUri();
    return ResponseEntity.accepted().location(location).body(response);
  }

  @GetMapping("/operations/{operationId}")
  @PreAuthorize("hasAnyRole('READ', 'OPERATOR', 'REVIEWER')")
  public OperationResponse getOperation(@PathVariable UUID operationId) {
    return OperationResponse.from(operationService.findById(operationId));
  }

  @PostMapping("/operations/{operationId}/reconcile")
  @PreAuthorize("hasAnyRole('OPERATOR', 'REVIEWER')")
  public OperationResponse reconcile(
      @PathVariable UUID operationId, Authentication authentication) {
    return OperationResponse.from(
        operationService.reconcile(operationId, authentication.getName()));
  }

  @PostMapping("/operations/{operationId}/resume")
  @PreAuthorize("hasRole('OPERATOR')")
  public OperationResponse resumeRenewal(
      @PathVariable UUID operationId,
      @Valid @RequestBody ResumeRenewalRequest request,
      Authentication authentication) {
    return OperationResponse.from(
        contractOperationService.resumeRenewal(
            operationId, request.ipcRateId(), authentication.getName()));
  }

  private IssuePolicyCommand toCommand(IssuePolicyRequest request) {
    return new IssuePolicyCommand(
        request.policyType(),
        request.holderPartyId(),
        request.startsOn(),
        request.durationMonths(),
        request.risks().stream()
            .map(
                risk ->
                    new IssueRiskCommand(
                        risk.propertyAddress(),
                        risk.tenantPartyId(),
                        risk.landlordPartyId(),
                        risk.monthlyRent()))
            .toList(),
        request.notes());
  }
}
