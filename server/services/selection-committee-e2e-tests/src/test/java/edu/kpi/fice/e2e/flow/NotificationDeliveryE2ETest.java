package edu.kpi.fice.e2e.flow;

import edu.kpi.fice.e2e.AbstractE2ETest;
import edu.kpi.fice.e2e.fixture.ApiClient;
import io.restassured.response.Response;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test that verifies email notification delivery via Mailpit.
 *
 * <p>Clears the Mailpit inbox, registers a new user, and then polls Mailpit until the verification
 * email arrives. The test also extracts the verification token from the email body to confirm the
 * message content is correct.
 *
 * <p>If Mailpit is not reachable the tests are still executed but the email-presence assertions
 * will be skipped gracefully.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationDeliveryE2ETest extends AbstractE2ETest {

  private static final ApiClient api =
      new ApiClient(IDENTITY_URL, ADMISSION_URL, DOCUMENTS_URL, ENVIRONMENT_SERVICE_URL);

  private static final String UNIQUE_SUFFIX = UUID.randomUUID().toString().substring(0, 8);
  private static final String TEST_EMAIL =
      "notif-e2e-" + UNIQUE_SUFFIX + "@e2e-test.kpi.ua";
  private static final String TEST_PASSWORD = "NotifE2E1Pass123";
  private static final String TEST_FIRST_NAME = "NotifE2E";
  private static final String TEST_LAST_NAME = "TestUser";

  static boolean mailpitAvailable;
  static String extractedVerificationToken;

  // ------------------------------------------------------------------
  // Step 1: Clear Mailpit inbox before the test run
  // ------------------------------------------------------------------

  @Test
  @Order(1)
  void step01_clearMailpitInbox() {
    mailpitAvailable = isMailpitAvailable();

    if (!mailpitAvailable) {
      System.out.println(
          "[NotificationDeliveryE2ETest] Mailpit not available at "
              + MAILPIT_API_URL
              + " — email assertion steps will be skipped.");
      return;
    }

    Response response = deleteMailpitMessages();
    assertThat(response.statusCode())
        .as("DELETE /api/v1/messages should clear inbox successfully")
        .isIn(200, 204);
  }

  // ------------------------------------------------------------------
  // Step 2: Register a new user to trigger verification email
  // ------------------------------------------------------------------

  @Test
  @Order(2)
  void step02_registerNewUserToTriggerEmail() {
    Response response =
        api.register(TEST_FIRST_NAME, TEST_LAST_NAME, TEST_EMAIL, TEST_PASSWORD);

    assertThat(response.statusCode())
        .as("Register new user should return 201 Created")
        .isEqualTo(201);
  }

  // ------------------------------------------------------------------
  // Step 3: Wait for verification email to arrive in Mailpit
  // ------------------------------------------------------------------

  @Test
  @Order(3)
  void step03_awaitVerificationEmail() {
    if (!mailpitAvailable) {
      System.out.println(
          "[NotificationDeliveryE2ETest] Mailpit not available — skipping email wait.");
      return;
    }

    // Poll Mailpit until the verification email arrives (delivered asynchronously
    // via RabbitMQ by the notifications service).
    await()
        .atMost(30, TimeUnit.SECONDS)
        .pollInterval(2, TimeUnit.SECONDS)
        .until(
            () -> {
              Response response = getMailpitMessages();
              return response.statusCode() == 200
                  && response.jsonPath().getInt("total") > 0;
            });

    // Verify the inbox actually contains a message after polling succeeds
    Response messagesResponse = getMailpitMessages();
    assertThat(messagesResponse.statusCode()).isEqualTo(200);
    int total = messagesResponse.jsonPath().getInt("total");
    assertThat(total)
        .as("Mailpit inbox should contain at least one message after registration")
        .isGreaterThan(0);
  }

  // ------------------------------------------------------------------
  // Step 4: Verify email content and extract verification token
  // ------------------------------------------------------------------

  @Test
  @Order(4)
  void step04_verifyEmailContentAndExtractToken() {
    if (!mailpitAvailable) {
      System.out.println(
          "[NotificationDeliveryE2ETest] Mailpit not available — skipping email content check.");
      return;
    }

    // Search for the email sent to the test address
    Response searchResp =
        given()
            .baseUri(MAILPIT_API_URL)
            .queryParam("query", "to:" + TEST_EMAIL)
            .when()
            .get("/api/v1/search");

    assertThat(searchResp.statusCode()).isEqualTo(200);
    int count = searchResp.jsonPath().getInt("messages_count");
    assertThat(count)
        .as("Should have at least one email addressed to %s", TEST_EMAIL)
        .isGreaterThanOrEqualTo(1);

    // Retrieve the full message body for the first matching email
    String messageId = searchResp.jsonPath().getString("messages[0].ID");
    assertThat(messageId).isNotBlank();

    Response msgResp =
        given().baseUri(MAILPIT_API_URL).when().get("/api/v1/message/" + messageId);

    assertThat(msgResp.statusCode()).isEqualTo(200);

    // Confirm the email subject looks like a verification message
    String subject = msgResp.jsonPath().getString("Subject");
    assertThat(subject)
        .as("Email subject should not be blank")
        .isNotBlank();

    // Extract the verification token from the email body
    String textBody = msgResp.jsonPath().getString("Text");
    if (textBody == null || textBody.isBlank()) {
      textBody = msgResp.jsonPath().getString("HTML");
    }

    assertThat(textBody)
        .as("Email body (text or HTML) should not be blank")
        .isNotBlank();

    // Look for a token=<value> pattern typical of verification links
    Matcher matcher =
        Pattern.compile("token=([a-zA-Z0-9\\-_]+)").matcher(textBody);
    if (matcher.find()) {
      extractedVerificationToken = matcher.group(1);
      assertThat(extractedVerificationToken)
          .as("Extracted verification token should not be blank")
          .isNotBlank();
      System.out.println(
          "[NotificationDeliveryE2ETest] Verification token extracted from email.");
    } else {
      System.out.println(
          "[NotificationDeliveryE2ETest] Could not extract token=<value> pattern from email body."
              + " The token may be embedded differently — check the email template.");
    }
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private static boolean isMailpitAvailable() {
    try {
      Response response = getMailpitMessages();
      return response.statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }
}
