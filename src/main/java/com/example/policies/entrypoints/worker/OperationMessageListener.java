package com.example.policies.entrypoints.worker;

import com.example.policies.adapters.out.messaging.OutboxEnvelope;
import com.example.policies.adapters.out.messaging.RabbitMessagingConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class OperationMessageListener {

  private final OperationProcessor operationProcessor;
  private final ObjectMapper objectMapper;

  public OperationMessageListener(
      OperationProcessor operationProcessor, ObjectMapper objectMapper) {
    this.operationProcessor = operationProcessor;
    this.objectMapper = objectMapper;
  }

  @RabbitListener(
      queues = RabbitMessagingConfiguration.OPERATIONS_QUEUE,
      concurrency = "${worker.max-core-concurrency:2}")
  public void receive(String payload) throws Exception {
    OutboxEnvelope event = objectMapper.readValue(payload, OutboxEnvelope.class);
    if (event.eventType().equals("POLICY_ISSUE_REQUESTED")
        || event.eventType().equals("CORE_RECONCILIATION_REQUESTED")) {
      operationProcessor.process(event.operationId());
    }
  }
}
