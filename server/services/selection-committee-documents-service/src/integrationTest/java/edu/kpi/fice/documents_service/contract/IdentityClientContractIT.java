package edu.kpi.fice.documents_service.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kpi.fice.documents_service.AbstractIntegrationTest;
import edu.kpi.fice.documents_service.common.dto.request.AuthUserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Identity Client Contract IT — AuthUserDto forward/backward compatibility")
class IdentityClientContractIT extends AbstractIntegrationTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Nested
  @DisplayName("POST /api/v1/auth/user response compatibility")
  class AuthUserEndpointContract {

    @Test
    @DisplayName(
        "Response with extra unknown fields deserializes without error (forward compatibility)")
    void extraUnknownFields_deserializedWithoutError() {
      // Simulate a response from identity-service that includes fields not yet in our DTO —
      // verifies that documents-service won't break when identity-service adds new fields
      String jsonWithExtraFields =
          """
              {
                  "id": 1,
                  "email": "test@example.com",
                  "role": "APPLICANT",
                  "firstName": "Test",
                  "middleName": null,
                  "lastName": "User",
                  "permissions": ["loading_documents", "reading_documents"],
                  "phoneNumber": "+380501234567",
                  "avatarUrl": "https://example.com/avatar.png",
                  "createdAt": "2026-01-01T00:00:00Z",
                  "newFutureField": {"nested": true},
                  "internalScore": 98.5
              }
              """;

      assertThatCode(
              () -> {
                AuthUserDto user = objectMapper.readValue(jsonWithExtraFields, AuthUserDto.class);
                assertThat(user.id()).isEqualTo(1L);
                assertThat(user.email()).isEqualTo("test@example.com");
                assertThat(user.role()).isEqualTo("APPLICANT");
                assertThat(user.firstName()).isEqualTo("Test");
                assertThat(user.lastName()).isEqualTo("User");
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Response with missing optional fields deserializes without error")
    void missingOptionalFields_deserializedWithoutError() {
      // Only required fields present — optional name fields omitted
      String jsonMinimal =
          """
              {
                  "id": 2,
                  "email": "minimal@example.com",
                  "role": "OPERATOR"
              }
              """;

      assertThatCode(
              () -> {
                AuthUserDto user = objectMapper.readValue(jsonMinimal, AuthUserDto.class);
                assertThat(user.id()).isEqualTo(2L);
                assertThat(user.email()).isEqualTo("minimal@example.com");
                assertThat(user.role()).isEqualTo("OPERATOR");
                assertThat(user.firstName()).isNull();
                assertThat(user.middleName()).isNull();
                assertThat(user.lastName()).isNull();
                assertThat(user.permissions()).isNull();
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Response with all fields present deserializes correctly (required fields present)")
    void allFieldsPresent_deserializesCorrectly() {
      String jsonFull =
          """
              {
                  "id": 3,
                  "email": "full@example.com",
                  "role": "ADMIN",
                  "firstName": "Full",
                  "middleName": "M",
                  "lastName": "User",
                  "permissions": ["loading_documents", "reading_documents", "checking_documents"]
              }
              """;

      assertThatCode(
              () -> {
                AuthUserDto user = objectMapper.readValue(jsonFull, AuthUserDto.class);
                assertThat(user.id()).isEqualTo(3L);
                assertThat(user.email()).isEqualTo("full@example.com");
                assertThat(user.role()).isEqualTo("ADMIN");
                assertThat(user.firstName()).isEqualTo("Full");
                assertThat(user.middleName()).isEqualTo("M");
                assertThat(user.lastName()).isEqualTo("User");
                assertThat(user.permissions())
                    .containsExactlyInAnyOrder(
                        "loading_documents", "reading_documents", "checking_documents");
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Response with null permissions collection deserializes without error")
    void nullPermissions_deserializedWithoutError() {
      String jsonWithNullPermissions =
          """
              {
                  "id": 4,
                  "email": "noperms@example.com",
                  "role": "APPLICANT",
                  "permissions": null
              }
              """;

      assertThatCode(
              () -> {
                AuthUserDto user =
                    objectMapper.readValue(jsonWithNullPermissions, AuthUserDto.class);
                assertThat(user.id()).isEqualTo(4L);
                assertThat(user.permissions()).isNull();
              })
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("Required fields validation")
  class RequiredFieldsContract {

    @Test
    @DisplayName("Consumer depends on id, email, and role fields being present")
    void consumerDependsOn_id_email_role() {
      // These are the fields that Documents service actually reads from AuthUserDto
      // to perform authorization. Verify all are accessible.
      String jsonWithRequiredFields =
          """
              {
                  "id": 99,
                  "email": "auth@example.com",
                  "role": "EXECUTIVE_SECRETARY",
                  "permissions": ["checking_documents", "working_with_contracts"]
              }
              """;

      assertThatCode(
              () -> {
                AuthUserDto user =
                    objectMapper.readValue(jsonWithRequiredFields, AuthUserDto.class);
                assertThat(user.id()).isNotNull();
                assertThat(user.email()).isNotBlank();
                assertThat(user.role()).isNotBlank();
                assertThat(user.permissions())
                    .containsExactlyInAnyOrder(
                        "checking_documents", "working_with_contracts");
              })
          .doesNotThrowAnyException();
    }
  }
}
