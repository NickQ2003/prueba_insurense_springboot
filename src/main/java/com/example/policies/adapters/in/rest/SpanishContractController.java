package com.example.policies.adapters.in.rest;

import com.example.policies.adapters.in.rest.dto.InitialRiskRequest;
import com.example.policies.adapters.in.rest.dto.OperationResponse;
import com.example.policies.adapters.in.rest.dto.RenewPolicyRequest;
import com.example.policies.adapters.in.rest.dto.SpanishRiskResponse;
import com.example.policies.application.usecase.AddRiskCommand;
import com.example.policies.application.usecase.ContractOperationService;
import com.example.policies.application.usecase.PolicyQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@Validated
public class SpanishContractController {

  private final PolicyQueryService policyQueryService;
  private final ContractOperationService contractOperationService;

  public SpanishContractController(
      PolicyQueryService policyQueryService, ContractOperationService contractOperationService) {
    this.policyQueryService = policyQueryService;
    this.contractOperationService = contractOperationService;
  }

  @GetMapping("/polizas/{policyId}/riesgos")
  @PreAuthorize("hasAnyRole('READ', 'OPERATOR', 'REVIEWER')")
  public List<SpanishRiskResponse> risks(@PathVariable UUID policyId) {
    return policyQueryService.findRisks(policyId).stream().map(SpanishRiskResponse::from).toList();
  }

  @PostMapping("/polizas/{policyId}/riesgos")
  @PreAuthorize("hasRole('OPERATOR')")
  public ResponseEntity<OperationResponse> addRisk(
      @PathVariable UUID policyId,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
      @Valid @RequestBody InitialRiskRequest request,
      Authentication authentication) {
    return accepted(
        contractOperationService.addRisk(
            policyId,
            new AddRiskCommand(
                request.propertyAddress(),
                request.tenantPartyId(),
                request.landlordPartyId(),
                request.monthlyRent()),
            idempotencyKey,
            authentication.getName()));
  }

  @PostMapping("/riesgos/{riskId}/cancelar")
  @PreAuthorize("hasRole('OPERATOR')")
  public ResponseEntity<OperationResponse> cancelRisk(
      @PathVariable UUID riskId,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
      Authentication authentication) {
    return accepted(
        contractOperationService.cancelRiskById(riskId, idempotencyKey, authentication.getName()));
  }

  @PostMapping("/polizas/{policyId}/renovar")
  @PreAuthorize("hasRole('OPERATOR')")
  public ResponseEntity<OperationResponse> renewPolicy(
      @PathVariable UUID policyId,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
      @RequestBody(required = false) RenewPolicyRequest request,
      Authentication authentication) {
    UUID ipcRateId = request == null ? null : request.ipcRateId();
    return accepted(
        contractOperationService.renewPolicy(
            policyId, ipcRateId, idempotencyKey, authentication.getName()));
  }

  @PostMapping("/polizas/{policyId}/cancelar")
  @PreAuthorize("hasRole('OPERATOR')")
  public ResponseEntity<OperationResponse> cancelPolicy(
      @PathVariable UUID policyId,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
      Authentication authentication) {
    return accepted(
        contractOperationService.cancelPolicy(policyId, idempotencyKey, authentication.getName()));
  }

  private ResponseEntity<OperationResponse> accepted(
      com.example.policies.application.usecase.OperationSnapshot operation) {
    OperationResponse response = OperationResponse.from(operation);
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/operations/{operationId}")
            .buildAndExpand(response.id())
            .toUri();
    return ResponseEntity.accepted().location(location).body(response);
  }
}
