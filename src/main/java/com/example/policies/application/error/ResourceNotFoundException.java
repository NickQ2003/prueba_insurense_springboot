package com.example.policies.application.error;

public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String resourceName) {
    super(resourceName + " was not found");
  }
}
