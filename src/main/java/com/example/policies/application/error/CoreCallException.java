package com.example.policies.application.error;

public class CoreCallException extends RuntimeException {

  private final int statusCode;

  public CoreCallException(int statusCode) {
    super("CORE mock returned HTTP " + statusCode);
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}
