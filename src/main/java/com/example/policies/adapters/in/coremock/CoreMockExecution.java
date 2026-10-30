package com.example.policies.adapters.in.coremock;

import com.example.policies.application.model.CoreOperationResult;

public record CoreMockExecution(int httpStatus, CoreOperationResult result) {}
