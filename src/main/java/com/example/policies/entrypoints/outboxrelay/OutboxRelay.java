package com.example.policies.entrypoints.outboxrelay;

import com.example.policies.adapters.out.messaging.OutboxEnvelope;
import com.example.policies.adapters.out.messaging.RabbitMessagingConfiguration;
import com.example.policies.application.port.out.OutboxRepository;
import com.example.policies.application.usecase.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("outbox-relay")
public class OutboxRelay {

  private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);

  private final OutboxRepository outboxRepository;
  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;
  private final String leaseOwner = UUID.randomUUID().toString();
  private final int batchSize;

  public OutboxRelay(
      OutboxRepository outboxRepository,
      RabbitTemplate rabbitTemplate,
      ObjectMapper objectMapper,
      @Value("${outbox-relay.batch-size:20}") int batchSize) {
    this.outboxRepository = outboxRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${outbox-relay.poll-interval-ms:500}")
  public void publishAvailableEvents() {
    List<OutboxEvent> events = outboxRepository.claimReadyBatch(batchSize, leaseOwner);
    for (OutboxEvent event : events) {
      publish(event);
    }
  }

  private void publish(OutboxEvent event) {
    try {
      String body =
          objectMapper.writeValueAsString(
              new OutboxEnvelope(
                  event.id(), event.operationId(), event.eventType(), event.payload()));
      CorrelationData confirmation = new CorrelationData(event.id().toString());
      rabbitTemplate.convertAndSend(
          RabbitMessagingConfiguration.EXCHANGE,
          RabbitMessagingConfiguration.OPERATIONS_ROUTING_KEY,
          body,
          confirmation);
      CorrelationData.Confirm result = confirmation.getFuture().get(10, TimeUnit.SECONDS);
      if (!result.isAck() || confirmation.getReturned() != null) {
        throw new IllegalStateException("RabbitMQ did not confirm the outbox event");
      }
      if (!outboxRepository.markPublished(event, leaseOwner)) {
        LOGGER.warn("Outbox lease lost after publishing event {}", event.id());
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      release(event);
    } catch (Exception exception) {
      LOGGER.warn("Could not publish outbox event {}", event.id());
      release(event);
    }
  }

  private void release(OutboxEvent event) {
    int delaySeconds = Math.min(60, Math.max(2, event.attemptCount() * 2));
    outboxRepository.releaseForRetry(event, leaseOwner, delaySeconds);
  }
}
