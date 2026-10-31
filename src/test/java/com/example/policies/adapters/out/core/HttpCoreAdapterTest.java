package com.example.policies.adapters.out.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.policies.application.error.CoreCallException;
import com.example.policies.application.error.CoreResponseMismatchException;
import com.example.policies.application.model.CoreOperationRequest;
import com.example.policies.application.model.CoreOperationStatus;
import java.net.SocketTimeoutException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpCoreAdapterTest {

  @Test
  void submitsOperationWithStableIdentityAndDemoApiKey() {
    UUID operationId = UUID.randomUUID();
    String requestHash = "a".repeat(64);
    RestClient.Builder builder = RestClient.builder().baseUrl("http://core-mock:8081");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    HttpCoreAdapter adapter = new HttpCoreAdapter(builder.build(), "123456", true, "CONFIRM");

    server
        .expect(requestTo("http://core-mock:8081/mock-core/operations"))
        .andExpect(method(POST))
        .andExpect(header("x-api-key", "123456"))
        .andExpect(header("X-Demo-Core-Outcome", "CONFIRM"))
        .andExpect(
            content()
                .json(
                    """
            {"operation_id":"%s","operation_type":"ISSUE_POLICY","request_hash":"%s","requested_payload":"{}"}
            """
                        .formatted(operationId, requestHash)))
        .andRespond(
            withSuccess(
                """
                {"operation_id":"%s","request_hash":"%s","status":"CONFIRMED","core_reference":"MOCK-1","message":"confirmed"}
                """
                    .formatted(operationId, requestHash),
                MediaType.APPLICATION_JSON));

    var result =
        adapter.submit(new CoreOperationRequest(operationId, "ISSUE_POLICY", requestHash, "{}"));

    assertEquals(operationId, result.operationId());
    assertEquals(CoreOperationStatus.CONFIRMED, result.status());
    server.verify();
  }

  @Test
  void rejectsMismatchedOperationIdentityFromCore() {
    UUID operationId = UUID.randomUUID();
    String requestHash = "a".repeat(64);
    RestClient.Builder builder = RestClient.builder().baseUrl("http://core-mock:8081");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    HttpCoreAdapter adapter = new HttpCoreAdapter(builder.build(), "123456", false, "CONFIRM");
    UUID wrongOperationId = UUID.randomUUID();

    server
        .expect(requestTo("http://core-mock:8081/mock-core/operations"))
        .andExpect(header("x-api-key", "123456"))
        .andExpect(
            content()
                .json(
                    """
                    {"operation_id":"%s","operation_type":"ISSUE_POLICY","request_hash":"%s","requested_payload":"{}"}
                    """
                        .formatted(operationId, requestHash)))
        .andRespond(
            withSuccess(
                """
                {"operation_id":"%s","request_hash":"%s","status":"CONFIRMED","core_reference":"MOCK-1","message":"confirmed"}
                """
                    .formatted(wrongOperationId, requestHash),
                MediaType.APPLICATION_JSON));

    assertThrows(
        CoreResponseMismatchException.class,
        () ->
            adapter.submit(
                new CoreOperationRequest(operationId, "ISSUE_POLICY", requestHash, "{}")));
    server.verify();
  }

  @Test
  void classifiesTransportTimeoutAsUncertainCoreCallForReconciliation() {
    ClientHttpRequestFactory timeoutRequestFactory =
        (uri, method) -> {
          throw new SocketTimeoutException("simulated CORE timeout");
        };
    RestClient restClient =
        RestClient.builder()
            .baseUrl("http://core-mock:8081")
            .requestFactory(timeoutRequestFactory)
            .build();
    HttpCoreAdapter adapter = new HttpCoreAdapter(restClient, "123456", false, "CONFIRM");

    CoreCallException exception =
        assertThrows(
            CoreCallException.class,
            () ->
                adapter.submit(
                    new CoreOperationRequest(
                        UUID.randomUUID(), "ISSUE_POLICY", "a".repeat(64), "{}")));

    assertEquals(504, exception.statusCode());
  }
}
