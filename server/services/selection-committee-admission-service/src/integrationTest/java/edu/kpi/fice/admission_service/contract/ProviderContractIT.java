package edu.kpi.fice.admission_service.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kpi.fice.admission_service.AbstractIntegrationTest;
import edu.kpi.fice.admission_service.entity.Application;
import edu.kpi.fice.admission_service.entity.ApplicationStatus;
import edu.kpi.fice.admission_service.entity.Grade;
import edu.kpi.fice.admission_service.repository.ApplicationRepository;
import edu.kpi.fice.common.auth.dto.RoleDto;
import edu.kpi.fice.common.auth.dto.UserDto;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName(
    "Admission Service Provider Contract IT — response shape matches Documents service consumer expectations")
class ProviderContractIT extends AbstractIntegrationTest {

  private static final Long APPLICANT_USER_ID = 9960L;

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ApplicationRepository applicationRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  private Application testApplication;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update(
        "INSERT INTO identity.users (id) VALUES (?) ON CONFLICT DO NOTHING", APPLICANT_USER_ID);

    testApplication =
        applicationRepository.saveAndFlush(
            Application.builder()
                .applicant(APPLICANT_USER_ID)
                .sex(true)
                .status(ApplicationStatus.draft)
                .grade(Grade.bachelor)
                .build());

    when(identityServiceClient.getCurrentUser())
        .thenReturn(
            new UserDto(
                APPLICANT_USER_ID,
                "contract-test@example.com",
                new RoleDto(1L, "APPLICANT"),
                "Contract",
                null,
                "Test",
                Set.of()));
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update(
        "DELETE FROM admission.contracts WHERE application_id = ?", testApplication.getId());
    jdbcTemplate.update(
        "DELETE FROM admission.groups_applications WHERE application_id = ?",
        testApplication.getId());
    jdbcTemplate.update("DELETE FROM admission.applications WHERE id = ?", testApplication.getId());
  }

  @Nested
  @DisplayName("GET /api/v1/admissions/{id} — ApplicationDto response shape")
  class ApplicationByIdEndpointShape {

    @Test
    @DisplayName("Response contains all fields that Documents service ApplicationDto depends on")
    void applicationById_responseContainsExpectedFields() throws Exception {
      // Documents service's ApplicationDto depends on:
      // id, applicantUserId, operatorUserId, sex, status, grade, privilege, createdAt, group
      // (as per documents_service.common.dto.response.ApplicationDto)
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/admissions/{id}", testApplication.getId())
                      .header("Authorization", "Bearer test-token"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.id").value(testApplication.getId()))
              .andExpect(jsonPath("$.status").exists())
              .andExpect(jsonPath("$.grade").exists())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      JsonNode appJson = objectMapper.readTree(body);

      // Fields that documents-service ApplicationDto reads
      assertThat(appJson.has("id")).isTrue();
      assertThat(appJson.get("id").asLong()).isEqualTo(testApplication.getId());

      assertThat(appJson.has("applicantUserId")).isTrue();
      assertThat(appJson.get("applicantUserId").asLong()).isEqualTo(APPLICANT_USER_ID);

      // operatorUserId is optional — may be null for draft applications
      assertThat(appJson.has("operatorUserId")).isTrue();

      assertThat(appJson.has("sex")).isTrue();
      assertThat(appJson.get("sex").asBoolean()).isTrue();

      assertThat(appJson.has("status")).isTrue();
      assertThat(appJson.get("status").asText()).isEqualTo("draft");

      assertThat(appJson.has("grade")).isTrue();
      assertThat(appJson.get("grade").asText()).isEqualTo("bachelor");

      // privilege and group are optional nested objects — may be null
      assertThat(appJson.has("privilege")).isTrue();
      assertThat(appJson.has("group")).isTrue();
    }

    @Test
    @DisplayName("Response for non-existent application returns 404")
    void nonExistentApplication_returns404() throws Exception {
      // Documents service calls checkIfApplicationExists which relies on 404 to determine
      // whether the application exists before uploading documents
      mockMvc
          .perform(
              get("/api/v1/admissions/{id}", Long.MAX_VALUE)
                  .header("Authorization", "Bearer test-token"))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("GET /api/v1/admissions/check/{id} — existence check endpoint shape")
  class ApplicationCheckEndpointShape {

    @Test
    @DisplayName("Check endpoint returns 204 No Content when application exists")
    void existingApplication_returns204() throws Exception {
      // Documents service calls this endpoint as a guard before processing documents.
      // It relies on 204 No Content for success (no body required).
      mockMvc
          .perform(
              get("/api/v1/admissions/check/{id}", testApplication.getId())
                  .header("Authorization", "Bearer test-token"))
          .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Check endpoint returns 404 when application does not exist")
    void nonExistentApplication_check_returns404() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/admissions/check/{id}", Long.MAX_VALUE)
                  .header("Authorization", "Bearer test-token"))
          .andExpect(status().isNotFound());
    }
  }
}
