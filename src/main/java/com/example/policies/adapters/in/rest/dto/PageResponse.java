package com.example.policies.adapters.in.rest.dto;

import com.example.policies.application.usecase.PageResult;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.function.Function;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PageResponse<T>(List<T> items, int limit, long offset, long total) {

  public static <S, T> PageResponse<T> from(PageResult<S> page, Function<S, T> mapper) {
    return new PageResponse<>(
        page.items().stream().map(mapper).toList(), page.limit(), page.offset(), page.total());
  }
}
