package com.example.policies.entrypoints.process;

import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@Profile({"worker", "outbox-relay", "scheduler", "seed"})
@EnableScheduling
@EnableAutoConfiguration(
    exclude = {
      FlywayAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      JpaRepositoriesAutoConfiguration.class,
      SecurityAutoConfiguration.class,
      ManagementWebSecurityAutoConfiguration.class,
      UserDetailsServiceAutoConfiguration.class
    })
@ComponentScan(
    basePackages = {
      "com.example.policies.application.usecase",
      "com.example.policies.adapters.out.persistence",
      "com.example.policies.adapters.out.core",
      "com.example.policies.adapters.out.messaging",
      "com.example.policies.entrypoints.worker",
      "com.example.policies.entrypoints.outboxrelay",
      "com.example.policies.entrypoints.scheduler",
      "com.example.policies.entrypoints.seed"
    })
public class ProcessRoleConfiguration {}
