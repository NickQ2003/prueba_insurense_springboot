package com.example.policies.adapters.out.core;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CoreClientConfiguration {

  @Bean
  RestClient coreRestClient(
      RestClient.Builder builder,
      @Value("${core.base-url:http://localhost:8081}") String baseUrl,
      @Value("${core.timeout-seconds:2}") int timeoutSeconds) {
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build());
    requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
    return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
