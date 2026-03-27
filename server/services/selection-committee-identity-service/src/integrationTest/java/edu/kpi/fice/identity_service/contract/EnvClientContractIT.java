package edu.kpi.fice.identity_service.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kpi.fice.identity_service.AbstractIntegrationTest;
import edu.kpi.fice.identity_service.web.features.shared.FeatureFlags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Env Client Contract IT — FeatureFlags DTO forward/backward compatibility")
class EnvClientContractIT extends AbstractIntegrationTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Nested
  @DisplayName("GET /flags?scope=identity response compatibility")
  class EnvServiceFlagEndpointContract {

    @Test
    @DisplayName(
        "Response with only known flag field deserializes correctly (minimal required payload)")
    void knownFlagField_deserializesCorrectly() {
      // The environment service sends back a FeatureFlags-shaped object.
      // Identity service depends on authPasswordLoginEnabled being present and true/false.
      String jsonWithKnownFlag =
          """
              {
                  "authPasswordLoginEnabled": true
              }
              """;

      assertThatCode(
              () -> {
                FeatureFlags flags = objectMapper.readValue(jsonWithKnownFlag, FeatureFlags.class);
                assertThat(flags.getAuthPasswordLoginEnabled()).isTrue();
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
        "Response with extra unknown flag fields deserializes without error (forward compatibility)")
    void extraUnknownFields_deserializedWithoutError() {
      // When environment service adds new feature flags in the future,
      // identity service should not break
      String jsonWithExtraFlags =
          """
              {
                  "authPasswordLoginEnabled": false,
                  "newExperimentalFeature": true,
                  "betaRegistrationFlow": false,
                  "maintenanceModeEnabled": false,
                  "advancedAuditLogging": true
              }
              """;

      assertThatCode(
              () -> {
                FeatureFlags flags =
                    objectMapper.readValue(jsonWithExtraFlags, FeatureFlags.class);
                assertThat(flags.getAuthPasswordLoginEnabled()).isFalse();
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Response with missing authPasswordLoginEnabled falls back to default value")
    void missingFlag_defaultValueUsed() {
      // FeatureFlags initializes authPasswordLoginEnabled to true by default.
      // If the env service omits it, the default should hold.
      String jsonWithoutFlag = "{}";

      assertThatCode(
              () -> {
                FeatureFlags flags = objectMapper.readValue(jsonWithoutFlag, FeatureFlags.class);
                // Default value is true when not overridden by env service
                assertThat(flags.getAuthPasswordLoginEnabled()).isTrue();
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Response with authPasswordLoginEnabled set to false disables password login")
    void flagDisabled_passwordLoginDisabled() {
      String jsonFlagDisabled =
          """
              {
                  "authPasswordLoginEnabled": false
              }
              """;

      assertThatCode(
              () -> {
                FeatureFlags flags =
                    objectMapper.readValue(jsonFlagDisabled, FeatureFlags.class);
                assertThat(flags.getAuthPasswordLoginEnabled()).isFalse();
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("asMap() produces correctly keyed entries for Spring Environment integration")
    void asMap_producesCorrectlyKeyedEntries() {
      // Identity service uses FeatureFlags.asMap() to expose flags as Spring Environment
      // properties. The key format must be "feature-flags.<kebab-case-name>".
      FeatureFlags flags = new FeatureFlags();
      flags.setAuthPasswordLoginEnabled(true);

      assertThatCode(
              () -> {
                var map = flags.asMap();
                assertThat(map).containsKey("feature-flags.auth-password-login-enabled");
                assertThat(map.get("feature-flags.auth-password-login-enabled")).isEqualTo(true);
              })
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("FeatureFlags default state contract")
  class DefaultStateContract {

    @Test
    @DisplayName("Default FeatureFlags instance has authPasswordLoginEnabled set to true")
    void defaultInstance_authPasswordLoginEnabled_isTrue() {
      // Provider must ensure authPasswordLoginEnabled is included in the response.
      // Consumers rely on the default being true (password login enabled by default).
      FeatureFlags defaultFlags = new FeatureFlags();
      assertThat(defaultFlags.getAuthPasswordLoginEnabled()).isTrue();
    }
  }
}
