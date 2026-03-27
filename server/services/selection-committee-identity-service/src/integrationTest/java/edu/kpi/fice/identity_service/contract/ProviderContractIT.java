package edu.kpi.fice.identity_service.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kpi.fice.identity_service.AbstractIntegrationTest;
import edu.kpi.fice.identity_service.web.auth.persistence.entity.VerificationToken;
import edu.kpi.fice.identity_service.web.auth.persistence.repository.VerificationTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("Identity Service Provider Contract IT — response shape matches consumer expectations")
class ProviderContractIT extends AbstractIntegrationTest {

  private static final String TEST_EMAIL = "provider-contract@example.com";
  private static final String TEST_PASSWORD = "StrongPass1";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private VerificationTokenRepository verificationTokenRepository;

  @Nested
  @DisplayName("POST /api/v1/auth/user — UserDto response shape")
  class AuthUserEndpointShape {

    @Test
    @DisplayName("Response for authenticated user contains all fields that consumers depend on")
    @DirtiesContext
    void authenticatedUser_responseContainsExpectedFields() throws Exception {
      // Step 1: Register a user
      String registerBody =
          """
              {
                "firstName": "Contract",
                "lastName": "Test",
                "email": "%s",
                "password": "%s"
              }
              """
              .formatted(TEST_EMAIL, TEST_PASSWORD);

      mockMvc
          .perform(
              post("/api/v1/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(registerBody))
          .andExpect(status().isCreated());

      // Step 2: Verify email
      VerificationToken vt =
          verificationTokenRepository.findAll().stream()
              .findFirst()
              .orElseThrow(() -> new AssertionError("Verification token not found"));

      mockMvc
          .perform(
              post("/api/v1/auth/verify")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"token\": \"%s\"}".formatted(vt.getToken())))
          .andExpect(status().isOk());

      // Step 3: Login to get access token
      MvcResult loginResult =
          mockMvc
              .perform(
                  post("/api/v1/auth/login")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                              {
                                "email": "%s",
                                "password": "%s"
                              }
                              """
                              .formatted(TEST_EMAIL, TEST_PASSWORD)))
              .andExpect(status().isOk())
              .andReturn();

      String loginBody = loginResult.getResponse().getContentAsString();
      JsonNode loginJson = objectMapper.readTree(loginBody);
      String accessToken = loginJson.get("accessToken").asText();

      // Step 4: Call POST /api/v1/auth/user with the access token and verify response shape.
      // Admission service and Documents service both call this endpoint and expect:
      // id, email, role (with id and name), firstName, middleName, lastName, extraPermissions
      MvcResult result =
          mockMvc
              .perform(
                  post("/api/v1/auth/user")
                      .header("Authorization", "Bearer " + accessToken))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.id").exists())
              .andExpect(jsonPath("$.email").value(TEST_EMAIL))
              .andExpect(jsonPath("$.role").exists())
              .andExpect(jsonPath("$.role.id").exists())
              .andExpect(jsonPath("$.role.name").exists())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      JsonNode userJson = objectMapper.readTree(body);

      // Verify all fields that Admission service's UserDto (sc-auth-starter) depends on
      assertThat(userJson.has("id")).isTrue();
      assertThat(userJson.get("id").asLong()).isGreaterThan(0);

      assertThat(userJson.has("email")).isTrue();
      assertThat(userJson.get("email").asText()).isEqualTo(TEST_EMAIL);

      // role must be an object with id and name — not a plain string
      assertThat(userJson.has("role")).isTrue();
      assertThat(userJson.get("role").isObject()).isTrue();
      assertThat(userJson.get("role").has("id")).isTrue();
      assertThat(userJson.get("role").has("name")).isTrue();
    }
  }

  @Nested
  @DisplayName("GET /api/v1/admin/users/by-role — List<UserDto> response shape")
  class UsersByRoleEndpointShape {

    @Test
    @WithMockUser(
        username = "admin-user",
        authorities = {"administration", "ADMIN"})
    @DisplayName("Response is a JSON array and each element contains expected UserDto fields")
    void usersByRole_responseIsArrayWithExpectedFields() throws Exception {
      // Admission service calls GET /api/v1/admin/users/by-role?roleName=OPERATOR
      // It deserializes the response as List<UserDto> and depends on id, email, role fields
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/admin/users/by-role")
                      .param("roleName", "APPLICANT")
                      .header("Authorization", "Bearer test-token"))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      JsonNode arrayNode = objectMapper.readTree(body);

      assertThat(arrayNode.isArray()).isTrue();

      // If there are users, each must have the fields consumers depend on
      arrayNode.forEach(
          userNode -> {
            assertThat(userNode.has("id")).isTrue();
            assertThat(userNode.has("email")).isTrue();
            assertThat(userNode.has("role")).isTrue();
            assertThat(userNode.get("role").isObject()).isTrue();
            assertThat(userNode.get("role").has("id")).isTrue();
            assertThat(userNode.get("role").has("name")).isTrue();
          });
    }
  }
}
