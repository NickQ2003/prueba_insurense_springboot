package com.example.policies.adapters.in.rest;

import com.example.policies.adapters.in.rest.dto.ApiErrorResponse;
import com.example.policies.application.error.IdempotencyConflictException;
import com.example.policies.application.error.ResourceNotFoundException;
import com.example.policies.domain.error.PolicyRuleViolationException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> invalidRequest(MethodArgumentNotValidException exception) {
    Map<String, String> fields =
        exception.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));
    return error(
        HttpStatus.UNPROCESSABLE_ENTITY, "validation_error", "Request validation failed", fields);
  }

  @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
  ResponseEntity<ApiErrorResponse> invalidParameters(Exception exception) {
    return error(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "validation_error",
        "Request parameters are invalid",
        Map.of());
  }

  @ExceptionHandler(PolicyRuleViolationException.class)
  ResponseEntity<ApiErrorResponse> domainRule(PolicyRuleViolationException exception) {
    return error(
        HttpStatus.UNPROCESSABLE_ENTITY, "domain_rule_violation", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ApiErrorResponse> notFound(ResourceNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "not_found", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiErrorResponse> forbidden() {
    return error(HttpStatus.FORBIDDEN, "forbidden", "Insufficient permissions", Map.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiErrorResponse> conflict() {
    return error(
        HttpStatus.CONFLICT, "data_conflict", "The request conflicts with existing data", Map.of());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  ResponseEntity<ApiErrorResponse> idempotencyConflict(IdempotencyConflictException exception) {
    return error(HttpStatus.CONFLICT, "idempotency_conflict", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ApiErrorResponse> missingHeader() {
    return error(
        HttpStatus.BAD_REQUEST, "missing_header", "A required request header is missing", Map.of());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiErrorResponse> invalidArgument(IllegalArgumentException exception) {
    return error(
        HttpStatus.UNPROCESSABLE_ENTITY, "invalid_argument", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiErrorResponse> unreadableRequest() {
    return error(HttpStatus.BAD_REQUEST, "invalid_json", "Request body is invalid", Map.of());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiErrorResponse> invalidParameter() {
    return error(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "invalid_parameter",
        "A request parameter has an invalid value",
        Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> internalError() {
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal_error",
        "An unexpected error occurred",
        Map.of());
  }

  private ResponseEntity<ApiErrorResponse> error(
      HttpStatus status, String error, String message, Map<String, String> fields) {
    return ResponseEntity.status(status)
        .body(new ApiErrorResponse(Instant.now(), status.value(), error, message, fields));
  }
}
