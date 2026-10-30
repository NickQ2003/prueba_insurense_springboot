package com.example.policies;

import com.example.policies.entrypoints.coremock.CoreMockLauncher;
import com.example.policies.entrypoints.process.ProcessRoleLauncher;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PoliciesApplication {

  public static void main(String[] args) {
    if (Arrays.asList(args).contains("--process-role=core-mock")) {
      CoreMockLauncher.run(args);
      return;
    }
    for (String role : new String[] {"worker", "outbox-relay"}) {
      if (Arrays.asList(args).contains("--process-role=" + role)) {
        ProcessRoleLauncher.run(role, args);
        return;
      }
    }
    SpringApplication.run(PoliciesApplication.class, args);
  }
}
