package com.example.policies.adapters.in.coremock;

public class CoreRequestConflictException extends RuntimeException {

  public CoreRequestConflictException() {
    super("CORE operation identity was reused with a different request");
  }
}
