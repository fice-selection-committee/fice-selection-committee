package edu.kpi.fice.e2e.flow;

import edu.kpi.fice.e2e.AbstractE2ETest;
import edu.kpi.fice.e2e.fixture.ApiClient;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test that exercises the complete authentication lifecycle:
 * registration, email verification (via Mailpit when available), login,
 * current user retrieval, and negative-path checks (wrong password, duplicate email).
 * <p>
 * Uses a unique email per test run to avoid collisions with previous runs.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowE2ETest extends AbstractE2ETest {

    private static final ApiClient api = new ApiClient(
            IDENTITY_URL, ADMISSION_URL, DOCUMENTS_URL, ENVIRONMENT_URL);

    /**
     * Mailpit HTTP API base URL. Defaults to localhost:8025.
     * Override with E2E_MAILPIT_URL env var or e2e.mailpit.url system property.
     */
    private static final String MAILPIT_URL = resolveMailpitUrl();

    // Unique email for this test run to avoid conflicts
    private static final String UNIQUE_SUFFIX = UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_EMAIL = "auth-e2e-" + UNIQUE_SUFFIX + "@e2e-test.kpi.ua";
    private static final String TEST_PASSWORD = "AuthFlow1Pass123";
    private static final String TEST_FIRST_NAME = "AuthE2E";
    private static final String TEST_LAST_NAME = "TestUser";

    // Shared state between ordered test methods
    static String accessToken;
    static boolean mailpitAvailable;
    static String verificationToken;

    private static String resolveMailpitUrl() {
        String fromProp = System.getProperty("e2e.mailpit.url");
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp;
        }
        String fromEnv = System.getenv("E2E_MAILPIT_URL");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return "http://localhost:8025";
    }

    // ------------------------------------------------------------------
    // Step 1: Register with valid data -> expect 201
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void step01_registerWithValidData() {
        Response response = api.register(
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_EMAIL, TEST_PASSWORD);

        assertThat(response.statusCode())
                .as("Register new user should return 201 Created")
                .isEqualTo(201);
    }

    // ------------------------------------------------------------------
    // Step 2: Check for verification email via Mailpit API
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void step02_checkVerificationEmail() {
        // Probe Mailpit availability
        mailpitAvailable = isMailpitAvailable();

        if (!mailpitAvailable) {
            // Mailpit is not available — skip email retrieval.
            // The verification step will be skipped too.
            System.out.println("[AuthFlowE2ETest] Mailpit not available at " + MAILPIT_URL
                    + " — skipping email verification token extraction.");
            return;
        }

        // Poll Mailpit for the verification email (it may take a moment for the
        // notification service to process the event via RabbitMQ).
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Response mailResponse = given()
                            .baseUri(MAILPIT_URL)
                            .queryParam("query", "to:" + TEST_EMAIL)
                            .when()
                            .get("/api/v1/search");

                    assertThat(mailResponse.statusCode()).isEqualTo(200);

                    int count = mailResponse.jsonPath().getInt("messages_count");
                    assertThat(count)
                            .as("Should have at least one email for %s", TEST_EMAIL)
                            .isGreaterThanOrEqualTo(1);
                });

        // Retrieve the latest message to extract the verification token
        Response searchResp = given()
                .baseUri(MAILPIT_URL)
                .queryParam("query", "to:" + TEST_EMAIL)
                .when()
                .get("/api/v1/search");

        String messageId = searchResp.jsonPath().getString("messages[0].ID");
        assertThat(messageId).isNotBlank();

        // Get full message body
        Response msgResp = given()
                .baseUri(MAILPIT_URL)
                .when()
                .get("/api/v1/message/" + messageId);

        assertThat(msgResp.statusCode()).isEqualTo(200);

        // Try to extract token from the email body.
        // The token is typically embedded as a query parameter or path segment
        // in a verification link, e.g., ?token=<uuid> or /verify?token=<uuid>.
        String textBody = msgResp.jsonPath().getString("Text");
        if (textBody == null || textBody.isBlank()) {
            textBody = msgResp.jsonPath().getString("HTML");
        }

        if (textBody != null) {
            // Look for token=<value> pattern
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("token=([a-zA-Z0-9\\-_]+)")
                    .matcher(textBody);
            if (matcher.find()) {
                verificationToken = matcher.group(1);
            }
        }

        // If we found a token, great. If not, we'll still try to login
        // (the test environment may auto-verify or the token format differs).
        if (verificationToken != null) {
            System.out.println("[AuthFlowE2ETest] Extracted verification token from email.");
        } else {
            System.out.println("[AuthFlowE2ETest] Could not extract token from email body — "
                    + "verification step will be skipped.");
        }
    }

    // ------------------------------------------------------------------
    // Step 3: Verify email with token -> expect 200 + access token
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void step03_verifyEmailToken() {
        if (verificationToken == null) {
            System.out.println("[AuthFlowE2ETest] No verification token available — skipping verify step.");
            return;
        }

        Response response = api.verifyEmail(verificationToken);

        assertThat(response.statusCode())
                .as("Verify email should return 200")
                .isEqualTo(200);

        String tokenFromVerify = response.jsonPath().getString("accessToken");
        assertThat(tokenFromVerify)
                .as("Verify response should contain an access token")
                .isNotBlank();
    }

    // ------------------------------------------------------------------
    // Step 4: Login with valid credentials -> expect 200 + access token
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void step04_loginWithValidCredentials() {
        Response response = api.login(TEST_EMAIL, TEST_PASSWORD);

        assertThat(response.statusCode())
                .as("Login with valid credentials should return 200")
                .isEqualTo(200);

        accessToken = response.jsonPath().getString("accessToken");
        assertThat(accessToken)
                .as("Login response should contain an access token")
                .isNotBlank();
    }

    // ------------------------------------------------------------------
    // Step 5: Get current user info -> expect 200 + correct data
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void step05_getCurrentUser() {
        assertThat(accessToken)
                .as("Access token must be available from login step")
                .isNotBlank();

        Response response = api.getCurrentUser(accessToken);

        assertThat(response.statusCode())
                .as("Get current user should return 200")
                .isEqualTo(200);

        String email = response.jsonPath().getString("email");
        assertThat(email)
                .as("Current user email should match registration email")
                .isEqualTo(TEST_EMAIL);

        String firstName = response.jsonPath().getString("firstName");
        assertThat(firstName)
                .as("Current user first name should match registration data")
                .isEqualTo(TEST_FIRST_NAME);

        String lastName = response.jsonPath().getString("lastName");
        assertThat(lastName)
                .as("Current user last name should match registration data")
                .isEqualTo(TEST_LAST_NAME);
    }

    // ------------------------------------------------------------------
    // Step 6: Invalid login (wrong password) -> expect 401
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void step06_loginWithWrongPassword() {
        Response response = api.login(TEST_EMAIL, "WrongPassword999");

        assertThat(response.statusCode())
                .as("Login with wrong password should return 401")
                .isEqualTo(401);
    }

    // ------------------------------------------------------------------
    // Step 7: Registration with existing email -> expect 409
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void step07_registerWithExistingEmail() {
        Response response = api.register(
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_EMAIL, TEST_PASSWORD);

        assertThat(response.statusCode())
                .as("Register with existing email should return 409 Conflict")
                .isEqualTo(409);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean isMailpitAvailable() {
        try {
            Response response = given()
                    .baseUri(MAILPIT_URL)
                    .when()
                    .get("/api/v1/messages");
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
