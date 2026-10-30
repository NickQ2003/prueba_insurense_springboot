package com.example.policies.application.error;

public class CoreResponseMismatchException extends RuntimeException {

  public CoreResponseMismatchException() {
    super("CORE response did not match the operation identity");
  }
}
