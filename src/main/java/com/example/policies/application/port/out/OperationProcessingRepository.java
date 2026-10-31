package com.example.policies.application.port.out;

import com.example.policies.application.model.CoreOperationResult;
import com.example.policies.application.usecase.IssuePolicyCommand;
import com.example.policies.application.usecase.OperationWork;
import com.example.policies.application.usecase.PolicyCancellationCommand;
import com.example.policies.application.usecase.PolicyRenewalCommand;
import com.example.policies.application.usecase.RiskCancellationCommand;
import com.example.policies.application.usecase.RiskCreationCommand;
import java.util.Optional;
import java.util.UUID;

public interface OperationProcessingRepository {

  Optional<OperationWork> claim(UUID operationId, String leaseOwner);

  void scheduleReconciliation(OperationWork operation, String errorCode);

  void markNeedsReview(OperationWork operation, String errorCode);

  void markRejected(OperationWork operation, CoreOperationResult coreResult);

  void completePolicyIssue(
      OperationWork operation, IssuePolicyCommand request, CoreOperationResult coreResult);

  void completeRiskAddition(
      OperationWork operation, RiskCreationCommand request, CoreOperationResult coreResult);

  void completeRiskCancellation(
      OperationWork operation, RiskCancellationCommand request, CoreOperationResult coreResult);

  void completePolicyCancellation(
      OperationWork operation, PolicyCancellationCommand request, CoreOperationResult coreResult);

  void completePolicyRenewal(
      OperationWork operation, PolicyRenewalCommand request, CoreOperationResult coreResult);
}
