package com.example.policies.entrypoints.worker;

import com.example.policies.application.error.CoreCallException;
import com.example.policies.application.error.CoreResponseMismatchException;
import com.example.policies.application.model.CoreOperationRequest;
import com.example.policies.application.model.CoreOperationResult;
import com.example.policies.application.model.CoreOperationStatus;
import com.example.policies.application.port.out.CorePort;
import com.example.policies.application.port.out.OperationProcessingRepository;
import com.example.policies.application.usecase.IssuePolicyCommand;
import com.example.policies.application.usecase.OperationWork;
import com.example.policies.domain.model.OperationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("worker")
public class OperationProcessor {

  private final OperationProcessingRepository operationRepository;
  private final CorePort corePort;
  private final ObjectMapper objectMapper;
  private final String workerId = UUID.randomUUID().toString();

  public OperationProcessor(
      OperationProcessingRepository operationRepository,
      CorePort corePort,
      ObjectMapper objectMapper) {
    this.operationRepository = operationRepository;
    this.corePort = corePort;
    this.objectMapper = objectMapper;
  }

  public void process(UUID operationId) {
    Optional<OperationWork> claimed = operationRepository.claim(operationId, workerId);
    if (claimed.isEmpty()) {
      return;
    }
    OperationWork operation = claimed.get();

    try {
      CoreOperationResult result =
          operation.status() == OperationStatus.RECONCILING
              ? reconcile(operation)
              : submit(operation);
      if (result == null) {
        operationRepository.markNeedsReview(operation, "CORE_RESULT_NOT_VERIFIABLE");
        return;
      }
      if (!operation.id().equals(result.operationId())
          || !operation.requestHash().equals(result.requestHash())) {
        operationRepository.markNeedsReview(operation, "CORE_RESPONSE_MISMATCH");
        return;
      }
      completeFromCoreResult(operation, result);
    } catch (CoreResponseMismatchException exception) {
      operationRepository.markNeedsReview(operation, "CORE_RESPONSE_MISMATCH");
    } catch (CoreCallException exception) {
      if (operation.status() == OperationStatus.PROCESSING) {
        operationRepository.scheduleReconciliation(
            operation, "CORE_HTTP_" + exception.statusCode());
      } else {
        operationRepository.markNeedsReview(operation, "CORE_RECONCILIATION_UNAVAILABLE");
      }
    } catch (Exception exception) {
      operationRepository.markNeedsReview(operation, "WORKER_PROCESSING_ERROR");
    }
  }

  private CoreOperationResult submit(OperationWork operation) {
    return corePort.submit(
        new CoreOperationRequest(
            operation.id(),
            operation.operationType(),
            operation.requestHash(),
            operation.requestedPayload()));
  }

  private CoreOperationResult reconcile(OperationWork operation) {
    return corePort.findByOperationId(operation.id()).orElse(null);
  }

  private void completeFromCoreResult(OperationWork operation, CoreOperationResult result)
      throws Exception {
    if (result.status() == CoreOperationStatus.REJECTED) {
      operationRepository.markRejected(operation, result);
      return;
    }
    if (!"ISSUE_POLICY".equals(operation.operationType())) {
      operationRepository.markNeedsReview(operation, "UNSUPPORTED_OPERATION_TYPE");
      return;
    }
    IssuePolicyCommand command =
        objectMapper.readValue(operation.requestedPayload(), IssuePolicyCommand.class);
    operationRepository.completePolicyIssue(operation, command, result);
  }
}
