package com.example.policies.entrypoints.coremock;

import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("core-mock")
@EnableAutoConfiguration(
    exclude = {
      DataSourceAutoConfiguration.class,
      FlywayAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      RabbitAutoConfiguration.class,
      ManagementWebSecurityAutoConfiguration.class,
      SecurityAutoConfiguration.class,
      UserDetailsServiceAutoConfiguration.class
    })
@ComponentScan("com.example.policies.adapters.in.coremock")
public class CoreMockConfiguration {}
