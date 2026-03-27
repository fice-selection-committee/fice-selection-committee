package edu.kpi.fice.service.environment.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kpi.fice.service.environment.AbstractIntegrationTest;
import edu.kpi.fice.service.environment.features.persistence.entity.FeatureFlag;
import edu.kpi.fice.service.environment.features.persistence.repository.JpaFeatureFlagRepository;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName(
    "Environment Service Provider Contract IT — response shape matches Identity service consumer expectations")
class ProviderContractIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JpaFeatureFlagRepository repository;

  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void setUp() {
    evictAllCaches();
    repository.deleteAll();

    // Seed the auth-password-login-enabled flag that identity service depends on
    FeatureFlag authLoginFlag =
        new FeatureFlag()
            .setKey("auth-password-login-enabled")
            .setEnabled(true)
            .setDescription("Controls whether password-based login is enabled")
            .setAllEnvironments(true);
    repository.saveAndFlush(authLoginFlag);
  }

  @AfterEach
  void tearDown() {
    evictAllCaches();
    repository.deleteAll();
  }

  @Nested
  @DisplayName("GET /api/v1/feature-flags?scope=identity — FeatureFlagResponse shape")
  class FeatureFlagsForIdentityScopeShape {

    @Test
    @WithMockUser(username = "service-user", authorities = {"USER"})
    @DisplayName(
        "Response is a JSON array where each element has the fields identity service depends on")
    void featureFlagsForIdentity_responseIsArrayWithExpectedFields() throws Exception {
      // Identity service's EnvServiceClient calls GET /flags?scope=identity and deserializes
      // the response into FeatureFlags. The critical field is authPasswordLoginEnabled.
      // The environment service exposes flags at /api/v1/feature-flags with an optional
      // scope query parameter.
      MvcResult result =
          mockMvc
              .perform(get("/api/v1/feature-flags").param("scope", "identity"))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      JsonNode arrayNode = objectMapper.readTree(body);

      assertThat(arrayNode.isArray()).isTrue();

      // Each flag object must have the contract fields that consumers can map
      arrayNode.forEach(
          flagNode -> {
            // key and enabled are the minimum fields consumers rely on
            assertThat(flagNode.has("key")).isTrue();
            assertThat(flagNode.has("enabled")).isTrue();
          });
    }

    @Test
    @WithMockUser(username = "service-user", authorities = {"USER"})
    @DisplayName("auth-password-login-enabled flag is present and has correct structure")
    void authPasswordLoginFlag_isPresent_withCorrectStructure() throws Exception {
      // Identity service depends on this specific flag to gate password login.
      // It must be present in the response for the identity scope.
      MvcResult result =
          mockMvc
              .perform(get("/api/v1/feature-flags"))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      JsonNode arrayNode = objectMapper.readTree(body);

      assertThat(arrayNode.isArray()).isTrue();

      boolean foundAuthFlag =
          arrayNode.findValues("key").stream()
              .anyMatch(node -> "auth-password-login-enabled".equals(node.asText()));
      assertThat(foundAuthFlag)
          .as("auth-password-login-enabled flag must be present in the response")
          .isTrue();

      // Find the flag and verify its enabled state is a boolean
      for (JsonNode flagNode : arrayNode) {
        if ("auth-password-login-enabled".equals(flagNode.get("key").asText())) {
          assertThat(flagNode.get("enabled").isBoolean())
              .as("enabled field must be a boolean")
              .isTrue();
          assertThat(flagNode.get("enabled").asBoolean()).isTrue();
          break;
        }
      }
    }

    @Test
    @WithMockUser(username = "service-user", authorities = {"USER"})
    @DisplayName("Individual flag response contains all FeatureFlagResponse fields")
    void individualFlag_responseContainsAllExpectedFields() throws Exception {
      // Verify that the full FeatureFlagResponse structure is present.
      // Identity service deserializes FeatureFlags from this response.
      MvcResult result =
          mockMvc
              .perform(get("/api/v1/feature-flags/auth-password-login-enabled"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.key").value("auth-password-login-enabled"))
              .andExpect(jsonPath("$.enabled").value(true))
              .andReturn();

      String body = result.getResponse().getContentAsString();
      JsonNode flagNode = objectMapper.readTree(body);

      // Verify FeatureFlagResponse contract fields
      assertThat(flagNode.has("key")).isTrue();
      assertThat(flagNode.get("key").asText()).isEqualTo("auth-password-login-enabled");

      assertThat(flagNode.has("enabled")).isTrue();
      assertThat(flagNode.get("enabled").asBoolean()).isTrue();

      assertThat(flagNode.has("description")).isTrue();
    }

    @Test
    @DisplayName("Unauthenticated request to feature flags endpoint returns 401")
    void unauthenticated_returns401() throws Exception {
      // Identity service calls this endpoint with a service token — it must require auth
      mockMvc.perform(get("/api/v1/feature-flags")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("GET /api/v1/feature-flags/{key}/enabled — boolean enabled check")
  class FlagEnabledEndpointShape {

    @Test
    @WithMockUser(username = "service-user", authorities = {"USER"})
    @DisplayName("Enabled check for known flag returns boolean true")
    void enabledCheck_knownFlag_returnsTrue() throws Exception {
      // Identity service can also check individual flag states.
      // Response must be a plain boolean value.
      MvcResult result =
          mockMvc
              .perform(get("/api/v1/feature-flags/auth-password-login-enabled/enabled"))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).isEqualToIgnoringCase("true");
    }
  }

  private void evictAllCaches() {
    cacheManager
        .getCacheNames()
        .forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
  }
}
