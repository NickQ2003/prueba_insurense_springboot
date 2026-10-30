package com.example.policies.application.usecase;

import java.util.List;

public record PageResult<T>(List<T> items, int limit, long offset, long total) {}
