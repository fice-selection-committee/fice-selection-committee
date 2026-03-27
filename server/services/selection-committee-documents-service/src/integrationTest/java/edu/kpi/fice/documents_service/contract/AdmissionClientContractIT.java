package edu.kpi.fice.documents_service.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kpi.fice.documents_service.AbstractIntegrationTest;
import edu.kpi.fice.documents_service.common.dto.response.ApplicationDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Admission Client Contract IT — ApplicationDto forward/backward compatibility")
class AdmissionClientContractIT extends AbstractIntegrationTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Test
  @DisplayName("Response with extra unknown fields deserializes without error")
  void extraFields_noError() {
    // ApplicationDto already has @JsonIgnoreProperties(ignoreUnknown = true)
    String jsonWithExtraFields =
        """
                {
                    "id": 100,
                    "applicantUserId": 1,
                    "operatorUserId": 2,
                    "sex": true,
                    "status": "accepted",
                    "grade": "bachelor",
                    "privilege": null,
                    "createdAt": null,
                    "group": null,
                    "rejectionReason": "some reason",
                    "newField": "should be ignored",
                    "anotherNewField": {"nested": "value"},
                    "priority": 5,
                    "internalScore": 98.5
                }
                """;

    assertThatCode(
            () -> {
              ApplicationDto dto =
                  objectMapper.readValue(jsonWithExtraFields, ApplicationDto.class);
              assertThat(dto.id()).isEqualTo(100L);
              assertThat(dto.applicantUserId()).isEqualTo(1L);
              assertThat(dto.operatorUserId()).isEqualTo(2L);
              assertThat(dto.status()).isEqualTo("accepted");
              assertThat(dto.grade()).isEqualTo("bachelor");
            })
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Response with missing optional fields deserializes without error")
  void missingOptionalFields_noError() {
    // Only required fields, everything optional is absent
    String jsonMinimal =
        """
                {
                    "id": 200,
                    "applicantUserId": 5,
                    "sex": false,
                    "status": "draft",
                    "grade": "master"
                }
                """;

    assertThatCode(
            () -> {
              ApplicationDto dto = objectMapper.readValue(jsonMinimal, ApplicationDto.class);
              assertThat(dto.id()).isEqualTo(200L);
              assertThat(dto.applicantUserId()).isEqualTo(5L);
              assertThat(dto.operatorUserId()).isNull();
              assertThat(dto.privilege()).isNull();
              assertThat(dto.createdAt()).isNull();
              assertThat(dto.group()).isNull();
            })
        .doesNotThrowAnyException();
  }
}
