package com.example.policies.application.port.out;

import com.example.policies.application.model.CoreOperationRequest;
import com.example.policies.application.model.CoreOperationResult;
import java.util.Optional;
import java.util.UUID;

public interface CorePort {

  CoreOperationResult submit(CoreOperationRequest request);

  Optional<CoreOperationResult> findByOperationId(UUID operationId);
}
