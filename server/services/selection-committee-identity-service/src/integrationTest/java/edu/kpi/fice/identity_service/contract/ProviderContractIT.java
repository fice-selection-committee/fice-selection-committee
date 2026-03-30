package edu.kpi.fice.identity_service.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kpi.fice.identity_service.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName(
    "Identity Service Provider Contract IT -- response shape matches consumer expectations")
class ProviderContractIT extends AbstractIntegrationTest {

  private static final String TEST_EMAIL = "provider-contract@example.com";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Nested
  @DisplayName("GET /api/v1/auth/user -- UserDto response shape")
  class AuthUserEndpointShape {

    @Test
    @DisplayName("Response for authenticated user contains all fields that consumers depend on")
    @DirtiesContext
    void authenticatedUser_responseContainsExpectedFields() throws Exception {
      // Step 1: Authenticate via magic link (auto-creates user)
      String rawToken = UUID.randomUUID().toString();
      String tokenHash = sha256Hex(rawToken);
      Instant expiry = Instant.now().plus(15, ChronoUnit.MINUTES);

      jdbcTemplate.update(
          "INSERT INTO identity.magic_link_tokens (token_hash, email, expiry_date, used) VALUES (?, ?, ?, false)",
          tokenHash,
          TEST_EMAIL,
          java.sql.Timestamp.from(expiry));

      MvcResult authResult =
          mockMvc
              .perform(
                  post("/api/v1/auth/verify-magic-link")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"token\": \"%s\"}".formatted(rawToken)))
              .andExpect(status().isOk())
              .andReturn();

      String authBody = authResult.getResponse().getContentAsString();
      JsonNode authJson = objectMapper.readTree(authBody);
      String accessToken = authJson.get("accessToken").asText();

      // Step 2: Call GET /api/v1/auth/user with the access token and verify response shape.
      // Admission service and Documents service both call this endpoint and expect:
      // id, email, role (with id and name), firstName, middleName, lastName, extraPermissions
      MvcResult result =
          mockMvc
              .perform(get("/api/v1/auth/user").header("Authorization", "Bearer " + accessToken))
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

      // role must be an object with id and name -- not a plain string
      assertThat(userJson.has("role")).isTrue();
      assertThat(userJson.get("role").isObject()).isTrue();
      assertThat(userJson.get("role").has("id")).isTrue();
      assertThat(userJson.get("role").has("name")).isTrue();
    }
  }

  @Nested
  @DisplayName("GET /api/v1/admin/users/by-role -- List<UserDto> response shape")
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
              .perform(get("/api/v1/admin/users/by-role").param("roleName", "APPLICANT"))
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

  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
