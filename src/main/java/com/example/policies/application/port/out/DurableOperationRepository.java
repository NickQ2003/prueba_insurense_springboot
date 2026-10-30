package com.example.policies.application.port.out;

import com.example.policies.application.usecase.OperationDraft;
import com.example.policies.application.usecase.OperationSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface DurableOperationRepository {

  OperationSnapshot createIdempotently(OperationDraft draft);

  Optional<OperationSnapshot> findById(UUID id);
}
