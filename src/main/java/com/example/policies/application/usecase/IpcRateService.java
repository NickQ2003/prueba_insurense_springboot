package com.example.policies.application.usecase;

import com.example.policies.application.port.out.IpcRateRepository;
import com.example.policies.domain.model.IpcRate;
import com.example.policies.domain.model.IpcRateValue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IpcRateService {

  private final IpcRateRepository ipcRateRepository;

  public IpcRateService(IpcRateRepository ipcRateRepository) {
    this.ipcRateRepository = ipcRateRepository;
  }

  @Transactional
  public IpcRate create(
      String referencePeriod,
      LocalDate effectiveOn,
      String rate,
      String source,
      boolean fictional) {
    IpcRate ipcRate =
        new IpcRate(
            UUID.randomUUID(),
            referencePeriod,
            effectiveOn,
            IpcRateValue.of(rate),
            source,
            fictional,
            Instant.now());
    return ipcRateRepository.save(ipcRate);
  }

  @Transactional(readOnly = true)
  public Optional<IpcRate> findById(UUID id) {
    return ipcRateRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public Optional<IpcRate> latestApplicable(LocalDate date) {
    return ipcRateRepository.findLatestApplicable(date);
  }

  @Transactional(readOnly = true)
  public PageResult<IpcRate> list(int limit, long offset) {
    return new PageResult<>(
        ipcRateRepository.findPage(limit, offset), limit, offset, ipcRateRepository.count());
  }
}
