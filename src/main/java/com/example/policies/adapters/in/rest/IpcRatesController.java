package com.example.policies.adapters.in.rest;

import com.example.policies.adapters.in.rest.dto.CreateIpcRateRequest;
import com.example.policies.adapters.in.rest.dto.IpcRateResponse;
import com.example.policies.adapters.in.rest.dto.PageResponse;
import com.example.policies.application.error.ResourceNotFoundException;
import com.example.policies.application.usecase.IpcRateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/ipc-rates")
@Validated
public class IpcRatesController {

  private final IpcRateService ipcRateService;

  public IpcRatesController(IpcRateService ipcRateService) {
    this.ipcRateService = ipcRateService;
  }

  @PostMapping
  @PreAuthorize("hasRole('OPERATOR')")
  public ResponseEntity<IpcRateResponse> create(@Valid @RequestBody CreateIpcRateRequest request) {
    IpcRateResponse response =
        IpcRateResponse.from(
            ipcRateService.create(
                request.referencePeriod(),
                request.effectiveOn(),
                request.rate(),
                request.source(),
                request.isFictional()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/ipc-rates/{ipcRateId}")
            .buildAndExpand(response.id())
            .toUri();
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('READ', 'OPERATOR', 'REVIEWER')")
  public PageResponse<IpcRateResponse> list(
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "0") @Min(0) long offset) {
    return PageResponse.from(ipcRateService.list(limit, offset), IpcRateResponse::from);
  }

  @GetMapping("/{ipcRateId}")
  @PreAuthorize("hasAnyRole('READ', 'OPERATOR', 'REVIEWER')")
  public IpcRateResponse get(@PathVariable UUID ipcRateId) {
    return IpcRateResponse.from(
        ipcRateService
            .findById(ipcRateId)
            .orElseThrow(() -> new ResourceNotFoundException("IPC rate")));
  }
}
