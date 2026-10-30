package com.example.policies.adapters.in.coremock;

import com.example.policies.application.model.CoreOperationRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("core-mock")
public class CoreMockController {

  private static final Logger LOGGER = LoggerFactory.getLogger(CoreMockController.class);

  private final CoreMockRegistry registry;
  private final String apiKey;
  private final boolean demoControlsEnabled;

  public CoreMockController(
      CoreMockRegistry registry,
      @Value("${demo.security.api-key:123456}") String apiKey,
      @Value("${demo.controls-enabled:true}") boolean demoControlsEnabled) {
    this.registry = registry;
    this.apiKey = apiKey;
    this.demoControlsEnabled = demoControlsEnabled;
  }

  @PostMapping("/mock-core/operations")
  public ResponseEntity<?> applyOperation(
      @RequestHeader(name = "x-api-key", required = false) String suppliedKey,
      @RequestHeader(name = "X-Demo-Core-Outcome", required = false) String requestedOutcome,
      @Valid @RequestBody CoreOperationRequest request) {
    if (!hasValidKey(suppliedKey)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    DemoCoreOutcome outcome = resolveOutcome(requestedOutcome);
    try {
      CoreMockExecution execution = registry.execute(request, outcome);
      return ResponseEntity.status(execution.httpStatus()).body(execution.result());
    } catch (CoreRequestConflictException exception) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(new CoreMockError("identity_mismatch"));
    }
  }

  @GetMapping("/mock-core/operations/{operationId}")
  public ResponseEntity<?> findOperation(
      @RequestHeader(name = "x-api-key", required = false) String suppliedKey,
      @PathVariable UUID operationId) {
    if (!hasValidKey(suppliedKey)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return registry
        .find(operationId)
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/core-mock/evento")
  public ResponseEntity<?> receiveDemoEvent(
      @RequestHeader(name = "x-api-key", required = false) String suppliedKey,
      @Valid @RequestBody CoreMockEventRequest event) {
    if (!hasValidKey(suppliedKey)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    LOGGER.info("Received compatibility CORE event type={}", event.event());
    return ResponseEntity.noContent().build();
  }

  private DemoCoreOutcome resolveOutcome(String requestedOutcome) {
    if (!demoControlsEnabled || requestedOutcome == null || requestedOutcome.isBlank()) {
      return DemoCoreOutcome.CONFIRM;
    }
    try {
      return DemoCoreOutcome.valueOf(requestedOutcome.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported demo CORE outcome");
    }
  }

  private boolean hasValidKey(String suppliedKey) {
    return suppliedKey != null
        && MessageDigest.isEqual(
            apiKey.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8));
  }

  private record CoreMockError(String error) {}
}
