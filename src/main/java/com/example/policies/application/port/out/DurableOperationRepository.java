package com.example.policies.application.port.out;

import com.example.policies.application.usecase.OperationDetails;
import com.example.policies.application.usecase.OperationDraft;
import com.example.policies.application.usecase.OperationSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface DurableOperationRepository {

  OperationSnapshot createIdempotently(OperationDraft draft);

  Optional<OperationSnapshot> findById(UUID id);

  Optional<OperationSnapshot> findByIdempotencyKey(
      String consumerScope, String operationType, String idempotencyKey);

  Optional<OperationDetails> findDetailsById(UUID id);

  boolean hasInFlightOperation(UUID policyId);

  boolean hasOtherInFlightOperation(UUID policyId, UUID operationId);

  OperationSnapshot requestReconciliation(UUID id, String actor);

  OperationSnapshot resumeWaitingInput(UUID id, String requestedPayload, String actor);
}
