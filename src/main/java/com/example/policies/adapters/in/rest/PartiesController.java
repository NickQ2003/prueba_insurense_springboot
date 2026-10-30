package com.example.policies.adapters.in.rest;

import com.example.policies.adapters.in.rest.dto.CreatePartyRequest;
import com.example.policies.adapters.in.rest.dto.PageResponse;
import com.example.policies.adapters.in.rest.dto.PartyResponse;
import com.example.policies.application.error.ResourceNotFoundException;
import com.example.policies.application.usecase.PartyService;
import com.example.policies.domain.model.PartyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/parties")
@Validated
public class PartiesController {

  private final PartyService partyService;

  public PartiesController(PartyService partyService) {
    this.partyService = partyService;
  }

  @PostMapping
  @PreAuthorize("hasRole('OPERATOR')")
  public ResponseEntity<PartyResponse> create(@Valid @RequestBody CreatePartyRequest request) {
    PartyResponse response =
        PartyResponse.from(
            partyService.create(
                request.partyType(), request.displayName(), request.documentReference()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/parties/{partyId}")
            .buildAndExpand(response.id())
            .toUri();
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('READ', 'OPERATOR', 'REVIEWER')")
  public PageResponse<PartyResponse> list(
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "0") @Min(0) long offset,
      @RequestParam(name = "party_type", required = false) PartyType partyType,
      @RequestParam(required = false) @Size(max = 100) String search) {
    return PageResponse.from(
        partyService.list(partyType, search, limit, offset), PartyResponse::from);
  }

  @GetMapping("/{partyId}")
  @PreAuthorize("hasAnyRole('READ', 'OPERATOR', 'REVIEWER')")
  public PartyResponse get(@PathVariable UUID partyId) {
    return PartyResponse.from(
        partyService.findById(partyId).orElseThrow(() -> new ResourceNotFoundException("Party")));
  }
}
