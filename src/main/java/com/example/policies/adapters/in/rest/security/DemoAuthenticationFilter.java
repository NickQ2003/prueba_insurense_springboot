package com.example.policies.adapters.in.rest.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DemoAuthenticationFilter extends OncePerRequestFilter {

  private final String apiKey;
  private final String readToken;
  private final String operatorToken;
  private final String reviewerToken;
  private final ObjectMapper objectMapper;

  public DemoAuthenticationFilter(
      @Value("${demo.security.api-key}") String apiKey,
      @Value("${demo.security.read-token}") String readToken,
      @Value("${demo.security.operator-token}") String operatorToken,
      @Value("${demo.security.reviewer-token}") String reviewerToken,
      ObjectMapper objectMapper) {
    this.apiKey = apiKey;
    this.readToken = readToken;
    this.operatorToken = operatorToken;
    this.reviewerToken = reviewerToken;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return path.equals("/actuator/health")
        || path.startsWith("/actuator/health/")
        || path.equals("/docs")
        || path.startsWith("/swagger-ui/")
        || path.startsWith("/v3/api-docs");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!matches(apiKey, request.getHeader("x-api-key"))) {
      reject(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized", "Invalid API key");
      return;
    }

    String authorization = request.getHeader("Authorization");
    String token = parseBearerToken(authorization);
    String role = resolveRole(token);
    if (role == null) {
      reject(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized", "Invalid bearer token");
      return;
    }

    var authentication =
        new UsernamePasswordAuthenticationToken(
            "demo-" + role.toLowerCase(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    filterChain.doFilter(request, response);
  }

  private String parseBearerToken(String authorization) {
    if (authorization == null) {
      return null;
    }
    if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) {
      return "";
    }
    return authorization.substring(7);
  }

  private String resolveRole(String token) {
    if (token == null) {
      return "OPERATOR";
    }
    if (matches(readToken, token)) {
      return "READ";
    }
    if (matches(operatorToken, token)) {
      return "OPERATOR";
    }
    if (matches(reviewerToken, token)) {
      return "REVIEWER";
    }
    return null;
  }

  private boolean matches(String expected, String actual) {
    return actual != null
        && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }

  private void reject(HttpServletResponse response, int status, String error, String message)
      throws IOException {
    SecurityContextHolder.clearContext();
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getWriter(), new AuthenticationError(status, error, message));
  }

  private record AuthenticationError(int status, String error, String message) {}
}
