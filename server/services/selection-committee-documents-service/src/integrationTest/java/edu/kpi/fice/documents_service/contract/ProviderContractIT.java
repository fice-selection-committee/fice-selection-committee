package edu.kpi.fice.documents_service.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kpi.fice.common.auth.client.IdentityServiceClient;
import edu.kpi.fice.common.auth.dto.PermissionDto;
import edu.kpi.fice.common.auth.dto.RoleDto;
import edu.kpi.fice.common.auth.dto.UserDto;
import edu.kpi.fice.documents_service.AbstractIntegrationTest;
import edu.kpi.fice.documents_service.entity.Document;
import edu.kpi.fice.documents_service.entity.DocumentStatus;
import edu.kpi.fice.documents_service.entity.DocumentType;
import edu.kpi.fice.documents_service.repository.DocumentRepository;
import edu.kpi.fice.sc.s3.S3Properties;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName(
    "Documents Service Provider Contract IT — response shape matches Admission service consumer expectations")
class ProviderContractIT extends AbstractIntegrationTest {

  private static final Long OWNER_USER_ID = 5001L;

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private DocumentRepository documentRepository;

  @Autowired private S3Properties s3Properties;

  @MockitoBean private IdentityServiceClient identityServiceClient;

  private Document testDocument;

  @BeforeEach
  void setUp() {
    // Mock authentication as a user with reading_documents permission.
    // Admission service calls GET /api/v1/documents/user/{userId} which requires this permission.
    UserDto readerUser =
        new UserDto(
            OWNER_USER_ID,
            "contract-reader@example.com",
            new RoleDto(1L, "APPLICANT"),
            "Contract",
            null,
            "Reader",
            Set.of(new PermissionDto(1L, "reading_documents")));
    when(identityServiceClient.getCurrentUser()).thenReturn(readerUser);

    testDocument = new Document();
    testDocument.setLink("documents/contract-test/" + java.util.UUID.randomUUID() + ".pdf");
    testDocument.setType(DocumentType.passport);
    testDocument.setOwnerUserId(OWNER_USER_ID);
    testDocument.setStatus(DocumentStatus.PENDING);
    testDocument.setEncryption(s3Properties.sse());
    testDocument.setBucket(s3Properties.bucket());
    testDocument.setContentType("application/pdf");
    testDocument.setSizeBytes(2048L);
    testDocument.setChecksum("contracttestchecksum");
    testDocument.setMetadata(objectMapper.createObjectNode());
    testDocument = documentRepository.save(testDocument);
  }

  @AfterEach
  void tearDown() {
    documentRepository.deleteAll();
  }

  @Nested
  @DisplayName("GET /api/v1/documents/user/{userId} — DocumentDto list response shape")
  class DocumentsByUserEndpointShape {

    @Test
    @DisplayName(
        "Response is a JSON array containing DocumentDto fields that Admission service depends on")
    void documentsByUser_responseContainsExpectedFields() throws Exception {
      // Admission service's DocumentsServiceClient calls GET /api/v1/documents?ownerUserId=X
      // and deserializes the response as List<DocumentDto>.
      // DocumentDto (admission-service) depends on: id, link, type, ownerUserId, status,
      // checksum, sizeBytes, contentType, encryption, bucket
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/documents/user/{userId}", OWNER_USER_ID)
                      .header("Authorization", "Bearer test-token"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$").isArray())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      JsonNode arrayNode = objectMapper.readTree(body);

      assertThat(arrayNode.isArray()).isTrue();
      assertThat(arrayNode.size()).isGreaterThanOrEqualTo(1);

      JsonNode docNode = arrayNode.get(0);

      // Verify all fields that admission-service's DocumentDto depends on
      assertThat(docNode.has("id")).isTrue();
      assertThat(docNode.get("id").asLong()).isEqualTo(testDocument.getId());

      assertThat(docNode.has("link")).isTrue();
      assertThat(docNode.get("link").asText()).isNotBlank();

      assertThat(docNode.has("type")).isTrue();
      assertThat(docNode.get("type").asText()).isEqualTo("passport");

      assertThat(docNode.has("ownerUserId")).isTrue();
      assertThat(docNode.get("ownerUserId").asLong()).isEqualTo(OWNER_USER_ID);

      assertThat(docNode.has("status")).isTrue();
      assertThat(docNode.get("status").asText()).isNotBlank();

      assertThat(docNode.has("checksum")).isTrue();
      assertThat(docNode.get("checksum").asText()).isEqualTo("contracttestchecksum");

      assertThat(docNode.has("sizeBytes")).isTrue();
      assertThat(docNode.get("sizeBytes").asLong()).isEqualTo(2048L);

      assertThat(docNode.has("contentType")).isTrue();
      assertThat(docNode.get("contentType").asText()).isEqualTo("application/pdf");

      assertThat(docNode.has("encryption")).isTrue();
      assertThat(docNode.has("bucket")).isTrue();
    }

    @Test
    @DisplayName("Response for user with no documents returns empty array")
    void userWithNoDocuments_returnsEmptyArray() throws Exception {
      // Admission service must handle an empty list without error
      Long userWithNoDocs = 9999L;

      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/documents/user/{userId}", userWithNoDocs)
                      .header("Authorization", "Bearer test-token"))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      JsonNode arrayNode = objectMapper.readTree(body);

      assertThat(arrayNode.isArray()).isTrue();
      assertThat(arrayNode.size()).isZero();
    }
  }

  @Nested
  @DisplayName("GET /api/v1/documents/{id} — single DocumentDto response shape")
  class DocumentByIdEndpointShape {

    @Test
    @DisplayName("Single document response contains all fields that consumers depend on")
    void documentById_responseContainsExpectedFields() throws Exception {
      // Mock user with reading_documents permission for single document access
      UserDto checkerUser =
          new UserDto(
              OWNER_USER_ID,
              "contract-checker@example.com",
              new RoleDto(2L, "OPERATOR"),
              "Checker",
              null,
              "User",
              Set.of(new PermissionDto(2L, "reading_documents")));
      when(identityServiceClient.getCurrentUser()).thenReturn(checkerUser);

      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/documents/{id}", testDocument.getId())
                      .header("Authorization", "Bearer test-token"))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      JsonNode docNode = objectMapper.readTree(body);

      assertThat(docNode.has("id")).isTrue();
      assertThat(docNode.get("id").asLong()).isEqualTo(testDocument.getId());

      assertThat(docNode.has("link")).isTrue();
      assertThat(docNode.has("type")).isTrue();
      assertThat(docNode.has("ownerUserId")).isTrue();
      assertThat(docNode.has("status")).isTrue();
      assertThat(docNode.has("checksum")).isTrue();
      assertThat(docNode.has("sizeBytes")).isTrue();
      assertThat(docNode.has("contentType")).isTrue();
      assertThat(docNode.has("encryption")).isTrue();
      assertThat(docNode.has("bucket")).isTrue();
    }
  }
}
