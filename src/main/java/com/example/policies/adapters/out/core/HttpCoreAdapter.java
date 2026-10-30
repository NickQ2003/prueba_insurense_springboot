package com.example.policies.adapters.out.core;

import com.example.policies.application.error.CoreCallException;
import com.example.policies.application.error.CoreResponseMismatchException;
import com.example.policies.application.model.CoreOperationRequest;
import com.example.policies.application.model.CoreOperationResult;
import com.example.policies.application.port.out.CorePort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpCoreAdapter implements CorePort {

  private final RestClient restClient;
  private final String apiKey;
  private final boolean demoControlsEnabled;
  private final String demoOutcome;

  public HttpCoreAdapter(
      RestClient coreRestClient,
      @Value("${demo.security.api-key:123456}") String apiKey,
      @Value("${demo.controls-enabled:true}") boolean demoControlsEnabled,
      @Value("${demo.core-outcome:CONFIRM}") String demoOutcome) {
    this.restClient = coreRestClient;
    this.apiKey = apiKey;
    this.demoControlsEnabled = demoControlsEnabled;
    this.demoOutcome = demoOutcome;
  }

  @Override
  public CoreOperationResult submit(CoreOperationRequest request) {
    try {
      RestClient.RequestBodySpec requestSpec =
          restClient
              .post()
              .uri("/mock-core/operations")
              .header("x-api-key", apiKey)
              .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
      if (demoControlsEnabled) {
        requestSpec.header("X-Demo-Core-Outcome", demoOutcome);
      }
      CoreOperationResult result =
          requestSpec.body(request).retrieve().body(CoreOperationResult.class);
      if (result == null
          || !request.operationId().equals(result.operationId())
          || !request.requestHash().equals(result.requestHash())) {
        throw new CoreResponseMismatchException();
      }
      return result;
    } catch (RestClientResponseException exception) {
      throw new CoreCallException(exception.getStatusCode().value());
    }
  }

  @Override
  public Optional<CoreOperationResult> findByOperationId(UUID operationId) {
    try {
      CoreOperationResult result =
          restClient
              .get()
              .uri("/mock-core/operations/{operationId}", operationId)
              .header("x-api-key", apiKey)
              .retrieve()
              .body(CoreOperationResult.class);
      if (result != null && !operationId.equals(result.operationId())) {
        throw new CoreResponseMismatchException();
      }
      return Optional.ofNullable(result);
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404) {
        return Optional.empty();
      }
      throw new CoreCallException(exception.getStatusCode().value());
    }
  }
}
