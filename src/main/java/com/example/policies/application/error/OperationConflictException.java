package com.example.policies.application.error;

public class OperationConflictException extends RuntimeException {

  public OperationConflictException(String message) {
    super(message);
  }
}
