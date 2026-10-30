package com.example.policies.entrypoints.process;

import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class ProcessRoleLauncher {

  private ProcessRoleLauncher() {}

  public static void run(String role, String[] args) {
    String roleArgument = "--process-role=" + role;
    String[] applicationArgs =
        Arrays.stream(args)
            .filter(argument -> !argument.equals(roleArgument))
            .toArray(String[]::new);
    new SpringApplicationBuilder(ProcessRoleConfiguration.class)
        .web(WebApplicationType.NONE)
        .profiles(role)
        .run(applicationArgs);
  }
}
