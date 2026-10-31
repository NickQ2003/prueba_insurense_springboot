package com.example.policies.entrypoints.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.policies.application.error.CoreCallException;
import com.example.policies.application.model.CoreOperationRequest;
import com.example.policies.application.model.CoreOperationResult;
import com.example.policies.application.model.CoreOperationStatus;
import com.example.policies.application.port.out.CorePort;
import com.example.policies.application.port.out.OperationProcessingRepository;
import com.example.policies.application.usecase.IssuePolicyCommand;
import com.example.policies.application.usecase.OperationWork;
import com.example.policies.application.usecase.PolicyRenewalCommand;
import com.example.policies.application.usecase.RiskCreationCommand;
import com.example.policies.domain.model.OperationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperationProcessorTest {

  @Mock private OperationProcessingRepository operationRepository;
  @Mock private CorePort corePort;

  private OperationProcessor processor;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    processor = new OperationProcessor(operationRepository, corePort, objectMapper);
  }

  @Test
  void confirmsPolicyOnlyAfterCoreConfirmation() {
    OperationWork operation = operation(OperationStatus.PROCESSING);
    CoreOperationResult result = result(operation, CoreOperationStatus.CONFIRMED, "CORE-001");
    when(operationRepository.claim(eq(operation.id()), any())).thenReturn(Optional.of(operation));
    when(corePort.submit(any(CoreOperationRequest.class))).thenReturn(result);

    processor.process(operation.id());

    verify(operationRepository)
        .completePolicyIssue(eq(operation), any(IssuePolicyCommand.class), eq(result));
    verify(operationRepository, never()).markNeedsReview(any(), any());
  }

  @Test
  void recordsBusinessRejectionWithoutCreatingLocalPolicy() {
    OperationWork operation = operation(OperationStatus.PROCESSING);
    CoreOperationResult result = result(operation, CoreOperationStatus.REJECTED, null);
    when(operationRepository.claim(eq(operation.id()), any())).thenReturn(Optional.of(operation));
    when(corePort.submit(any(CoreOperationRequest.class))).thenReturn(result);

    processor.process(operation.id());

    verify(operationRepository).markRejected(operation, result);
    verify(operationRepository, never()).completePolicyIssue(any(), any(), any());
  }

  @Test
  void reconcilesUncertainSubmissionWithoutResendingMutation() {
    OperationWork operation = operation(OperationStatus.PROCESSING);
    when(operationRepository.claim(eq(operation.id()), any())).thenReturn(Optional.of(operation));
    when(corePort.submit(any(CoreOperationRequest.class))).thenThrow(new CoreCallException(504));

    processor.process(operation.id());

    verify(operationRepository).scheduleReconciliation(operation, "CORE_HTTP_504");
    verify(corePort, never()).findByOperationId(any());
  }

  @Test
  void marksUnverifiableReconciliationForReview() {
    OperationWork operation = operation(OperationStatus.RECONCILING);
    when(operationRepository.claim(eq(operation.id()), any())).thenReturn(Optional.of(operation));
    when(corePort.findByOperationId(operation.id())).thenReturn(Optional.empty());

    processor.process(operation.id());

    verify(corePort, never()).submit(any());
    verify(operationRepository).markNeedsReview(operation, "CORE_RESULT_NOT_VERIFIABLE");
  }

  @Test
  void neverAcceptsCoreResponseForDifferentRequestHash() {
    OperationWork operation = operation(OperationStatus.PROCESSING);
    CoreOperationResult result =
        new CoreOperationResult(
            operation.id(),
            "b".repeat(64),
            CoreOperationStatus.CONFIRMED,
            "CORE-OTHER",
            "synthetic mismatch");
    when(operationRepository.claim(eq(operation.id()), any())).thenReturn(Optional.of(operation));
    when(corePort.submit(any(CoreOperationRequest.class))).thenReturn(result);

    processor.process(operation.id());

    verify(operationRepository).markNeedsReview(operation, "CORE_RESPONSE_MISMATCH");
    verify(operationRepository, never()).completePolicyIssue(any(), any(), any());
  }

  @Test
  void dispatchesConfirmedRiskAdditionToContractCompletion() {
    UUID policyId = UUID.randomUUID();
    UUID periodId = UUID.randomUUID();
    OperationWork operation =
        operation(
            "ADD_RISK",
            """
            {"policy_id":"%s","period_id":"%s","property_address":"Synthetic property","tenant_party_id":"11111111-1111-4111-8111-111111111111","landlord_party_id":"22222222-2222-4222-8222-222222222222","monthly_rent":"2500000.00"}
            """
                .formatted(policyId, periodId),
            OperationStatus.PROCESSING);
    CoreOperationResult result = result(operation, CoreOperationStatus.CONFIRMED, "CORE-RISK-001");
    when(operationRepository.claim(eq(operation.id()), any())).thenReturn(Optional.of(operation));
    when(corePort.submit(any(CoreOperationRequest.class))).thenReturn(result);

    processor.process(operation.id());

    verify(operationRepository)
        .completeRiskAddition(eq(operation), any(RiskCreationCommand.class), eq(result));
    verify(operationRepository, never()).markNeedsReview(any(), any());
  }

  @Test
  void dispatchesConfirmedRenewalWithItsPersistedRiskSnapshot() {
    UUID policyId = UUID.randomUUID();
    UUID sourcePeriodId = UUID.randomUUID();
    UUID newPeriodId = UUID.randomUUID();
    UUID ipcRateId = UUID.randomUUID();
    UUID riskId = UUID.randomUUID();
    OperationWork operation =
        operation(
            "RENEW_POLICY",
            """
            {"policy_id":"%s","source_period_id":"%s","new_period_id":"%s","starts_on":"2027-10-30","ends_on":"2028-10-30","duration_months":12,"calculation_version":"demo-rent-v1","rate_required":false,"ipc_rate_id":"%s","reference_period":"DEMO-2027","ipc_rate_snapshot":"0.08000","effective_on":"2027-10-01","risk_values":[{"risk_id":"%s","previous_monthly_rent":"100000.00","monthly_rent":"108000.00","premium":"1296000.00","currency":"COP"}]}
            """
                .formatted(policyId, sourcePeriodId, newPeriodId, ipcRateId, riskId),
            OperationStatus.PROCESSING);
    CoreOperationResult result =
        result(operation, CoreOperationStatus.CONFIRMED, "CORE-RENEWAL-001");
    when(operationRepository.claim(eq(operation.id()), any())).thenReturn(Optional.of(operation));
    when(corePort.submit(any(CoreOperationRequest.class))).thenReturn(result);

    processor.process(operation.id());

    verify(operationRepository)
        .completePolicyRenewal(eq(operation), any(PolicyRenewalCommand.class), eq(result));
    verify(operationRepository, never()).markNeedsReview(any(), any());
  }

  private OperationWork operation(OperationStatus status) {
    return operation(
        "ISSUE_POLICY",
        """
        {"policy_type":"INDIVIDUAL","holder_party_id":"11111111-1111-4111-8111-111111111111","starts_on":"2026-10-30","duration_months":12,"risks":[{"property_address":"Synthetic address","tenant_party_id":"11111111-1111-4111-8111-111111111111","landlord_party_id":"22222222-2222-4222-8222-222222222222","monthly_rent":"1500000.00"}]}
        """,
        status);
  }

  private OperationWork operation(String type, String payload, OperationStatus status) {
    return new OperationWork(
        UUID.randomUUID(),
        type,
        "a".repeat(64),
        payload,
        UUID.randomUUID(),
        status,
        1,
        "worker-test");
  }

  private CoreOperationResult result(
      OperationWork operation, CoreOperationStatus status, String coreReference) {
    return new CoreOperationResult(
        operation.id(), operation.requestHash(), status, coreReference, "synthetic result");
  }
}
