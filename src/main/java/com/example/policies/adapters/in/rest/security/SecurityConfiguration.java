package com.example.policies.adapters.in.rest.security;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  @Bean
  FilterRegistrationBean<DemoAuthenticationFilter> disableServletFilterRegistration(
      DemoAuthenticationFilter filter) {
    FilterRegistrationBean<DemoAuthenticationFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, DemoAuthenticationFilter demoAuthenticationFilter) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorization ->
                authorization
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/docs",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions.accessDeniedHandler(
                    (request, response, accessDeniedException) -> {
                      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                      response.setContentType("application/json");
                      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                      response.getWriter().write("{\"status\":403,\"error\":\"forbidden\"}");
                    }))
        .addFilterBefore(demoAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
