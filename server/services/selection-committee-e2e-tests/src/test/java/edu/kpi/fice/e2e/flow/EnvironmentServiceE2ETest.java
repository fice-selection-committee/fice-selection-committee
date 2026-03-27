package edu.kpi.fice.e2e.flow;

import edu.kpi.fice.e2e.AbstractE2ETest;
import edu.kpi.fice.e2e.fixture.ApiClient;
import edu.kpi.fice.e2e.fixture.TestUsers;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the Environment Service, exercised through the API gateway.
 *
 * <p>Covers feature-flag listing, scope listing, and verifies that unauthenticated requests are
 * rejected with 401.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EnvironmentServiceE2ETest extends AbstractE2ETest {

  private static final ApiClient api =
      new ApiClient(IDENTITY_URL, ADMISSION_URL, DOCUMENTS_URL, ENVIRONMENT_SERVICE_URL);

  static String adminToken;

  // ------------------------------------------------------------------
  // Setup: login as admin
  // ------------------------------------------------------------------

  @BeforeAll
  static void loginAsAdmin() {
    Response response = api.login(TestUsers.ADMIN_EMAIL, TestUsers.ADMIN_PASSWORD);
    assertThat(response.statusCode()).as("Admin login for EnvironmentServiceE2ETest").isEqualTo(200);
    adminToken = response.jsonPath().getString("accessToken");
    assertThat(adminToken).isNotBlank();
  }

  // ------------------------------------------------------------------
  // Step 1: List feature flags for a specific scope -> 200
  // ------------------------------------------------------------------

  @Test
  @Order(1)
  void step01_listFeatureFlagsForIdentityScope() {
    Response response =
        given()
            .baseUri(GATEWAY_URL)
            .header("Authorization", "Bearer " + adminToken)
            .queryParam("scope", "identity")
            .when()
            .get("/api/v1/feature-flags");

    assertThat(response.statusCode())
        .as("GET /api/v1/feature-flags?scope=identity should return 200")
        .isEqualTo(200);
  }

  // ------------------------------------------------------------------
  // Step 2: List all scopes -> 200
  // ------------------------------------------------------------------

  @Test
  @Order(2)
  void step02_listScopes() {
    Response response =
        given()
            .baseUri(GATEWAY_URL)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/api/v1/scopes");

    assertThat(response.statusCode())
        .as("GET /api/v1/scopes should return 200")
        .isEqualTo(200);
  }

  // ------------------------------------------------------------------
  // Step 3: List all feature flags without scope filter -> 200
  // ------------------------------------------------------------------

  @Test
  @Order(3)
  void step03_listAllFeatureFlags() {
    Response response =
        given()
            .baseUri(GATEWAY_URL)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/api/v1/feature-flags");

    assertThat(response.statusCode())
        .as("GET /api/v1/feature-flags without scope filter should return 200")
        .isEqualTo(200);
  }

  // ------------------------------------------------------------------
  // Step 4: Unauthenticated feature flags request -> 401
  // ------------------------------------------------------------------

  @Test
  @Order(4)
  void step04_unauthorizedFeatureFlagsRequest() {
    Response response =
        given()
            .baseUri(GATEWAY_URL)
            .when()
            .get("/api/v1/feature-flags");

    assertThat(response.statusCode())
        .as("GET /api/v1/feature-flags without auth token should return 401")
        .isEqualTo(401);
  }

  // ------------------------------------------------------------------
  // Step 5: Unauthenticated scopes request -> 401
  // ------------------------------------------------------------------

  @Test
  @Order(5)
  void step05_unauthorizedScopesRequest() {
    Response response =
        given()
            .baseUri(GATEWAY_URL)
            .when()
            .get("/api/v1/scopes");

    assertThat(response.statusCode())
        .as("GET /api/v1/scopes without auth token should return 401")
        .isEqualTo(401);
  }
}
