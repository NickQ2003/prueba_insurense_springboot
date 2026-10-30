package com.example.policies.application.port.out;

import com.example.policies.application.model.CoreOperationResult;
import com.example.policies.application.usecase.IssuePolicyCommand;
import com.example.policies.application.usecase.OperationWork;
import java.util.Optional;
import java.util.UUID;

public interface OperationProcessingRepository {

  Optional<OperationWork> claim(UUID operationId, String leaseOwner);

  void scheduleReconciliation(OperationWork operation, String errorCode);

  void markNeedsReview(OperationWork operation, String errorCode);

  void markRejected(OperationWork operation, CoreOperationResult coreResult);

  void completePolicyIssue(
      OperationWork operation, IssuePolicyCommand request, CoreOperationResult coreResult);
}
