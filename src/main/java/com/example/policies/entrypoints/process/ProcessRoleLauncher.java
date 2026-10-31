package com.example.policies.entrypoints.process;

import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class ProcessRoleLauncher {

  private ProcessRoleLauncher() {}

  public static void run(String role, String[] args) {
    String roleArgument = "--process-role=" + role;
    String[] applicationArgs =
        Arrays.stream(args)
            .filter(argument -> !argument.equals(roleArgument))
            .toArray(String[]::new);
    ConfigurableApplicationContext context =
        new SpringApplicationBuilder(ProcessRoleConfiguration.class)
            .web(WebApplicationType.NONE)
            .profiles(role)
            .run(applicationArgs);
    if (role.equals("seed")) {
      context.close();
    }
  }
}
