package com.example.policies.application.port.out;

import com.example.policies.domain.model.IpcRate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IpcRateRepository {

  IpcRate save(IpcRate rate);

  IpcRate saveIfAbsent(IpcRate rate);

  Optional<IpcRate> findById(UUID id);

  Optional<IpcRate> findLatestApplicable(LocalDate effectiveOn);

  List<IpcRate> findPage(int limit, long offset);

  long count();
}
