package edu.kpi.fice.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for all E2E tests.
 *
 * <p>Service URLs are resolved via system properties first, then environment variables, falling
 * back to localhost defaults. This allows the tests to run against a local dev environment, a
 * Docker Compose stack, or a CI environment without code changes.
 */
public abstract class AbstractE2ETest {

  protected static final String GATEWAY_URL =
      resolveUrl("e2e.gateway.url", "E2E_GATEWAY_URL", "http://localhost:8080");
  protected static final String IDENTITY_URL =
      resolveUrl("e2e.identity.url", "E2E_IDENTITY_URL", "http://localhost:8081");
  protected static final String ADMISSION_URL =
      resolveUrl("e2e.admission.url", "E2E_ADMISSION_URL", "http://localhost:8083");
  protected static final String DOCUMENTS_URL =
      resolveUrl("e2e.documents.url", "E2E_DOCUMENTS_URL", "http://localhost:8084");
  protected static final String ENVIRONMENT_URL =
      resolveUrl("e2e.environment.url", "E2E_ENVIRONMENT_URL", "http://localhost:8085");
  protected static final String ENVIRONMENT_SERVICE_URL =
      resolveUrl("e2e.environment.url", "E2E_ENVIRONMENT_URL", "http://localhost:8085");
  protected static final String MAILPIT_API_URL =
      resolveUrl("e2e.mailpit.url", "E2E_MAILPIT_URL", "http://localhost:8025");

  @BeforeAll
  static void verifyServicesRunning() {
    // Ping identity service as a basic liveness check.
    // If this fails the rest of the suite is pointless.
    try {
      Response response =
          RestAssured.given()
              .baseUri(IDENTITY_URL)
              .contentType(ContentType.JSON)
              .when()
              .get("/actuator/health");
      assertThat(response.statusCode())
          .as("Identity service must be reachable at %s", IDENTITY_URL)
          .isLessThan(500);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Cannot reach identity service at "
              + IDENTITY_URL
              + ". Make sure all services are running before executing E2E tests.",
          e);
    }
  }

  /**
   * GET {@code {MAILPIT_API_URL}/api/v1/messages} and return the raw response.
   *
   * @return the REST Assured response from the Mailpit messages endpoint
   */
  protected static Response getMailpitMessages() {
    return given().baseUri(MAILPIT_API_URL).when().get("/api/v1/messages");
  }

  /**
   * DELETE {@code {MAILPIT_API_URL}/api/v1/messages} to clear the Mailpit inbox.
   *
   * @return the REST Assured response from the delete request
   */
  protected static Response deleteMailpitMessages() {
    return given().baseUri(MAILPIT_API_URL).when().delete("/api/v1/messages");
  }

  private static String resolveUrl(String systemProperty, String envVar, String defaultValue) {
    String fromProp = System.getProperty(systemProperty);
    if (fromProp != null && !fromProp.isBlank()) {
      return fromProp;
    }
    String fromEnv = System.getenv(envVar);
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv;
    }
    return defaultValue;
  }
}
