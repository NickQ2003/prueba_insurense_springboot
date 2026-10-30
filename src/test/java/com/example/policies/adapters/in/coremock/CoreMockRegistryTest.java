package com.example.policies.adapters.in.coremock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.policies.application.model.CoreOperationRequest;
import com.example.policies.application.model.CoreOperationStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoreMockRegistryTest {

  private final CoreMockRegistry registry = new CoreMockRegistry();

  @Test
  void confirmsAndReplaysTheSameOperationIdempotently() {
    CoreOperationRequest request = request(UUID.randomUUID(), "a".repeat(64));

    CoreMockExecution first = registry.execute(request, DemoCoreOutcome.CONFIRM);
    CoreMockExecution replay = registry.execute(request, DemoCoreOutcome.REJECT);

    assertEquals(200, first.httpStatus());
    assertEquals(CoreOperationStatus.CONFIRMED, first.result().status());
    assertEquals(first.result(), replay.result());
  }

  @Test
  void rejectsReusingAnOperationIdentityWithDifferentPayload() {
    UUID operationId = UUID.randomUUID();
    registry.execute(request(operationId, "a".repeat(64)), DemoCoreOutcome.CONFIRM);

    assertThrows(
        CoreRequestConflictException.class,
        () -> registry.execute(request(operationId, "b".repeat(64)), DemoCoreOutcome.CONFIRM));
  }

  @Test
  void distinguishesTimeoutBeforeAndAfterApplyingAChange() {
    UUID beforeId = UUID.randomUUID();
    CoreMockExecution before =
        registry.execute(request(beforeId, "c".repeat(64)), DemoCoreOutcome.TIMEOUT_BEFORE_APPLY);
    assertEquals(504, before.httpStatus());
    assertFalse(registry.find(beforeId).isPresent());

    UUID afterId = UUID.randomUUID();
    CoreMockExecution after =
        registry.execute(request(afterId, "d".repeat(64)), DemoCoreOutcome.TIMEOUT_AFTER_APPLY);
    assertEquals(504, after.httpStatus());
    assertNotNull(registry.find(afterId).orElse(null));
  }

  @Test
  void unavailableCoreDoesNotRecordAnOperation() {
    UUID operationId = UUID.randomUUID();

    CoreMockExecution result =
        registry.execute(request(operationId, "0".repeat(64)), DemoCoreOutcome.UNAVAILABLE);

    assertEquals(503, result.httpStatus());
    assertFalse(registry.find(operationId).isPresent());
  }

  @Test
  void canReturnRejectAndMismatchOutcomes() {
    CoreMockExecution rejected =
        registry.execute(request(UUID.randomUUID(), "e".repeat(64)), DemoCoreOutcome.REJECT);
    assertEquals(CoreOperationStatus.REJECTED, rejected.result().status());

    UUID requestedId = UUID.randomUUID();
    CoreMockExecution mismatch =
        registry.execute(request(requestedId, "f".repeat(64)), DemoCoreOutcome.MISMATCH);
    assertNotNull(mismatch.result());
    org.junit.jupiter.api.Assertions.assertNotEquals(requestedId, mismatch.result().operationId());
  }

  private CoreOperationRequest request(UUID operationId, String requestHash) {
    return new CoreOperationRequest(
        operationId, "ISSUE_POLICY", requestHash, "{\"policy_type\":\"INDIVIDUAL\"}");
  }
}
