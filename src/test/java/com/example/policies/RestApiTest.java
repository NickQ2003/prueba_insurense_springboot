package com.example.policies;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.policies.adapters.in.rest.ApiExceptionHandler;
import com.example.policies.adapters.in.rest.security.SecurityConfiguration;
import com.example.policies.application.usecase.PageResult;
import com.example.policies.application.usecase.PartyService;
import com.example.policies.application.usecase.PolicyQueryService;
import com.example.policies.domain.model.Party;
import com.example.policies.domain.model.PartyType;
import com.example.policies.domain.model.PolicyStatus;
import com.example.policies.domain.model.PolicyType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import({SecurityConfiguration.class, ApiExceptionHandler.class})
class RestApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PartyService partyService;
  @MockitoBean private PolicyQueryService policyQueryService;

  @Test
  void rejectsBusinessRequestsWithoutApiKey() throws Exception {
    mockMvc.perform(get("/api/v1/parties")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/parties").header("x-api-key", "invalid"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createsPartyForDemoOperator() throws Exception {
    UUID partyId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-10-30T00:00:00Z");
    Party party =
        new Party(partyId, PartyType.PERSON, "Test party", "TEST-DOCUMENT-001", createdAt);
    given(partyService.create(PartyType.PERSON, "Test party", "TEST-DOCUMENT-001"))
        .willReturn(party);

    mockMvc
        .perform(
            post("/api/v1/parties")
                .header("x-api-key", "123456")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"party_type":"PERSON","display_name":"Test party","document_reference":"TEST-DOCUMENT-001"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "http://localhost/api/v1/parties/" + partyId))
        .andExpect(jsonPath("$.party_type").value("PERSON"))
        .andExpect(jsonPath("$.document_reference").value("TEST-DOCUMENT-001"));
  }

  @Test
  void readRoleCannotCreateParty() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/parties")
                .header("x-api-key", "123456")
                .header("Authorization", "Bearer demo-read-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"party_type":"PERSON","display_name":"Read-only party","document_reference":"READ-ONLY-001"}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("forbidden"));
  }

  @Test
  void validatesCreatePartyRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/parties")
                .header("x-api-key", "123456")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"party_type":"PERSON","display_name":" ","document_reference":""}
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").value("validation_error"));
  }

  @Test
  void enforcesMaximumPageSize() throws Exception {
    mockMvc
        .perform(get("/api/v1/parties").header("x-api-key", "123456").param("limit", "101"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").value("validation_error"));
  }

  @Test
  void readerCanQueryPartyAndPolicyPages() throws Exception {
    given(partyService.list(null, null, 50, 0)).willReturn(new PageResult<>(List.of(), 50, 0, 0));
    given(policyQueryService.list(null, null, 50, 0))
        .willReturn(new PageResult<>(List.of(), 50, 0, 0));

    mockMvc
        .perform(
            get("/api/v1/parties")
                .header("x-api-key", "123456")
                .header("Authorization", "Bearer demo-read-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.total").value(0));

    mockMvc
        .perform(
            get("/api/v1/policies")
                .header("x-api-key", "123456")
                .header("Authorization", "Bearer demo-read-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray());
  }

  @Test
  void spanishPolicyRouteMapsTypeAndStatus() throws Exception {
    given(
            policyQueryService.list(
                eq(PolicyType.COLLECTIVE), eq(PolicyStatus.ACTIVE), eq(100), eq(0L)))
        .willReturn(new PageResult<>(List.of(), 100, 0, 0));

    mockMvc
        .perform(
            get("/polizas")
                .header("x-api-key", "123456")
                .param("tipo", "COLECTIVA")
                .param("estado", "ACTIVA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }
}
