package com.example.policies.application.error;

public class CoreCallException extends RuntimeException {

  private final int statusCode;

  public CoreCallException(int statusCode) {
    this(statusCode, null);
  }

  public CoreCallException(int statusCode, Throwable cause) {
    super("CORE call failed with status " + statusCode, cause);
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}
