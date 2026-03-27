package edu.kpi.fice.e2e.flow;

import edu.kpi.fice.e2e.AbstractE2ETest;
import edu.kpi.fice.e2e.fixture.ApiClient;
import edu.kpi.fice.e2e.fixture.TestUsers;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests covering error and security-boundary scenarios.
 *
 * <p>Each test is independent — no shared mutable state is produced beyond the tokens obtained in
 * {@link #setupTokens()}. The suite verifies that the system correctly rejects unauthorized,
 * unauthenticated, and semantically invalid requests.
 */
class NegativeFlowE2ETest extends AbstractE2ETest {

  private static final ApiClient api =
      new ApiClient(IDENTITY_URL, ADMISSION_URL, DOCUMENTS_URL, ENVIRONMENT_SERVICE_URL);

  static String adminToken;
  static String applicantToken;
  static Long applicantUserId;

  @BeforeAll
  static void setupTokens() {
    // Admin (pre-seeded)
    Response adminLogin = api.login(TestUsers.ADMIN_EMAIL, TestUsers.ADMIN_PASSWORD);
    assertThat(adminLogin.statusCode()).as("Admin login for NegativeFlowE2ETest").isEqualTo(200);
    adminToken = adminLogin.jsonPath().getString("accessToken");

    // Register + login applicant (unique email per run)
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String applicantEmail = "neg-flow-" + suffix + "@e2e-test.kpi.ua";

    Response regResp =
        api.register(
            TestUsers.APPLICANT_FIRST_NAME,
            TestUsers.APPLICANT_LAST_NAME,
            applicantEmail,
            TestUsers.APPLICANT_PASSWORD);
    assertThat(regResp.statusCode())
        .as("Register applicant for NegativeFlowE2ETest")
        .isEqualTo(201);

    Response loginResp = api.login(applicantEmail, TestUsers.APPLICANT_PASSWORD);
    assertThat(loginResp.statusCode()).isEqualTo(200);
    applicantToken = loginResp.jsonPath().getString("accessToken");

    Response userResp = api.getCurrentUser(applicantToken);
    assertThat(userResp.statusCode()).isEqualTo(200);
    applicantUserId = userResp.jsonPath().getLong("id");
  }

  // ------------------------------------------------------------------
  // 1. Unauthorized access: no token -> 401
  // ------------------------------------------------------------------

  @Test
  void unauthorizedAccess_noToken_returns401() {
    Response response =
        given().baseUri(GATEWAY_URL).when().get("/api/v1/applications");

    assertThat(response.statusCode())
        .as("GET /api/v1/applications without any token should return 401")
        .isEqualTo(401);
  }

  // ------------------------------------------------------------------
  // 2. Invalid token -> 401
  // ------------------------------------------------------------------

  @Test
  void invalidToken_returns401() {
    Response response =
        given()
            .baseUri(GATEWAY_URL)
            .header("Authorization", "Bearer invalid-token")
            .when()
            .get("/api/v1/applications");

    assertThat(response.statusCode())
        .as("GET /api/v1/applications with a bogus token should return 401")
        .isEqualTo(401);
  }

  // ------------------------------------------------------------------
  // 3. Invalid registration data (bad email format) -> 400
  // ------------------------------------------------------------------

  @Test
  void register_invalidEmailFormat_returns400() {
    Response response =
        api.register(
            TestUsers.APPLICANT_FIRST_NAME,
            TestUsers.APPLICANT_LAST_NAME,
            "not-a-valid-email",
            TestUsers.APPLICANT_PASSWORD);

    assertThat(response.statusCode())
        .as("Registration with a malformed email should return 400 Bad Request")
        .isEqualTo(400);
  }

  // ------------------------------------------------------------------
  // 4. Duplicate registration -> 409 Conflict
  // ------------------------------------------------------------------

  @Test
  void register_duplicateEmail_returns409() {
    // Use the well-known admin email which is guaranteed to exist
    Response response =
        api.register(
            TestUsers.ADMIN_FIRST_NAME,
            TestUsers.ADMIN_LAST_NAME,
            TestUsers.ADMIN_EMAIL,
            TestUsers.ADMIN_PASSWORD);

    assertThat(response.statusCode())
        .as("Registering with an already-used email should return 409 Conflict")
        .isEqualTo(409);
  }

  // ------------------------------------------------------------------
  // 5. Non-existent resource -> 404
  // ------------------------------------------------------------------

  @Test
  void getApplication_nonExistentId_returns404() {
    Response response = api.getApplication(adminToken, 999999L);

    assertThat(response.statusCode())
        .as("GET /api/v1/admissions/999999 with valid admin token should return 404")
        .isEqualTo(404);
  }

  // ------------------------------------------------------------------
  // 6. Wrong role access: applicant tries admin-only endpoint -> 403
  // ------------------------------------------------------------------

  @Test
  void applicant_accessAdminEndpoint_returns403() {
    // GET /api/v1/admin/users is restricted to ADMIN role
    Response response = api.listUsers(applicantToken);

    assertThat(response.statusCode())
        .as("Applicant accessing /api/v1/admin/users should be forbidden (403)")
        .isEqualTo(403);
  }
}
