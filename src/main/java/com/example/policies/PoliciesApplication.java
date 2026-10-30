package com.example.policies;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PoliciesApplication {

  public static void main(String[] args) {
    SpringApplication.run(PoliciesApplication.class, args);
  }
}
