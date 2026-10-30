package com.example.policies.adapters.in.coremock;

import com.example.policies.application.model.CoreOperationRequest;
import com.example.policies.application.model.CoreOperationResult;
import com.example.policies.application.model.CoreOperationStatus;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("core-mock")
public class CoreMockRegistry {

  private final Map<UUID, CoreOperationResult> results = new ConcurrentHashMap<>();

  public synchronized CoreMockExecution execute(
      CoreOperationRequest request, DemoCoreOutcome requestedOutcome) {
    CoreOperationResult existing = results.get(request.operationId());
    if (existing != null) {
      if (!existing.requestHash().equals(request.requestHash())) {
        throw new CoreRequestConflictException();
      }
      return new CoreMockExecution(200, existing);
    }

    return switch (requestedOutcome) {
      case UNAVAILABLE -> new CoreMockExecution(503, null);
      case TIMEOUT_BEFORE_APPLY -> new CoreMockExecution(504, null);
      case TIMEOUT_AFTER_APPLY -> {
        CoreOperationResult applied = confirmed(request);
        results.put(request.operationId(), applied);
        yield new CoreMockExecution(504, null);
      }
      case REJECT -> {
        CoreOperationResult rejected =
            new CoreOperationResult(
                request.operationId(),
                request.requestHash(),
                CoreOperationStatus.REJECTED,
                null,
                "Synthetic CORE business rejection");
        results.put(request.operationId(), rejected);
        yield new CoreMockExecution(200, rejected);
      }
      case MISMATCH -> {
        CoreOperationResult mismatch =
            new CoreOperationResult(
                UUID.randomUUID(),
                request.requestHash(),
                CoreOperationStatus.CONFIRMED,
                "MOCK-MISMATCH-" + request.operationId(),
                "Synthetic CORE identity mismatch");
        results.put(request.operationId(), mismatch);
        yield new CoreMockExecution(200, mismatch);
      }
      case CONFIRM -> {
        CoreOperationResult confirmed = confirmed(request);
        results.put(request.operationId(), confirmed);
        yield new CoreMockExecution(200, confirmed);
      }
    };
  }

  public Optional<CoreOperationResult> find(UUID operationId) {
    return Optional.ofNullable(results.get(operationId));
  }

  private CoreOperationResult confirmed(CoreOperationRequest request) {
    return new CoreOperationResult(
        request.operationId(),
        request.requestHash(),
        CoreOperationStatus.CONFIRMED,
        "MOCK-" + request.operationId(),
        "Synthetic CORE confirmation");
  }
}
