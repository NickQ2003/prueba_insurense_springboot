package com.example.policies.adapters.out.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableRabbit
@Profile({"worker", "outbox-relay"})
public class RabbitMessagingConfiguration {

  public static final String EXCHANGE = "policies.events";
  public static final String OPERATIONS_QUEUE = "policies.operations";
  public static final String OPERATIONS_ROUTING_KEY = "policy.event";

  @Bean
  DirectExchange policiesExchange() {
    return new DirectExchange(EXCHANGE, true, false);
  }

  @Bean
  Queue operationsQueue() {
    return QueueBuilder.durable(OPERATIONS_QUEUE).build();
  }

  @Bean
  Binding operationsBinding(Queue operationsQueue, DirectExchange policiesExchange) {
    return BindingBuilder.bind(operationsQueue).to(policiesExchange).with(OPERATIONS_ROUTING_KEY);
  }
}
