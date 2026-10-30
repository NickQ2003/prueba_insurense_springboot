package com.example.policies.entrypoints.coremock;

import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class CoreMockLauncher {

  private CoreMockLauncher() {}

  public static void run(String[] args) {
    String[] applicationArgs =
        Arrays.stream(args)
            .filter(argument -> !argument.equals("--process-role=core-mock"))
            .toArray(String[]::new);
    new SpringApplicationBuilder(CoreMockConfiguration.class)
        .web(WebApplicationType.SERVLET)
        .profiles("core-mock")
        .run(applicationArgs);
  }
}
