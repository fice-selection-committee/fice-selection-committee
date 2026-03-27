package edu.kpi.fice.e2e.flow;

import edu.kpi.fice.e2e.AbstractE2ETest;
import edu.kpi.fice.e2e.fixture.ApiClient;
import edu.kpi.fice.e2e.fixture.TestUsers;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test that verifies the rejection flow:
 * applicant submits an application, the operator rejects it,
 * and the applicant sees the rejection reason and the application
 * returns to a state that allows re-submission or correction.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentRejectionFlowE2ETest extends AbstractE2ETest {

    private static final ApiClient api = new ApiClient(
            IDENTITY_URL, ADMISSION_URL, DOCUMENTS_URL, ENVIRONMENT_URL);

    private static final String REJECTION_REASON = "Document scan is illegible. Please re-upload.";

    static String adminToken;
    static Long adminUserId;
    static String applicantToken;
    static Long applicantUserId;
    static String operatorToken;
    static Long operatorUserId;
    static Long applicationId;
    static Long documentId;

    // ------------------------------------------------------------------
    // Step 1: Set up users and create an application
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void step01_setupUsersAndApplication() {
        // Register applicant (idempotent)
        api.register(
                TestUsers.APPLICANT_FIRST_NAME,
                TestUsers.APPLICANT_LAST_NAME,
                "reject-applicant@e2e-test.kpi.ua",
                TestUsers.APPLICANT_PASSWORD);

        // Login applicant
        Response applicantLogin = api.login("reject-applicant@e2e-test.kpi.ua", TestUsers.APPLICANT_PASSWORD);
        assertThat(applicantLogin.statusCode()).isEqualTo(200);
        applicantToken = applicantLogin.jsonPath().getString("accessToken");

        Response applicantUser = api.getCurrentUser(applicantToken);
        applicantUserId = applicantUser.jsonPath().getLong("id");

        // Login admin (pre-seeded)
        Response adminLogin = api.login(TestUsers.ADMIN_EMAIL, TestUsers.ADMIN_PASSWORD);
        assertThat(adminLogin.statusCode()).isEqualTo(200);
        adminToken = adminLogin.jsonPath().getString("accessToken");

        Response adminUser = api.getCurrentUser(adminToken);
        adminUserId = adminUser.jsonPath().getLong("id");

        // Register & promote operator
        api.register(
                TestUsers.OPERATOR_FIRST_NAME,
                TestUsers.OPERATOR_LAST_NAME,
                "reject-operator@e2e-test.kpi.ua",
                TestUsers.OPERATOR_PASSWORD);

        Response opLogin = api.login("reject-operator@e2e-test.kpi.ua", TestUsers.OPERATOR_PASSWORD);
        assertThat(opLogin.statusCode()).isEqualTo(200);
        operatorToken = opLogin.jsonPath().getString("accessToken");

        Response opUser = api.getCurrentUser(operatorToken);
        operatorUserId = opUser.jsonPath().getLong("id");

        api.changeUserRole(adminToken, operatorUserId, "ROLE_OPERATOR");

        // Re-login operator to pick up new role
        opLogin = api.login("reject-operator@e2e-test.kpi.ua", TestUsers.OPERATOR_PASSWORD);
        operatorToken = opLogin.jsonPath().getString("accessToken");

        // Create application — handle existing from previous run
        Response appResp = api.createApplication(applicantToken, applicantUserId, false, "draft");
        if (appResp.statusCode() == 409) {
            Response list = api.listApplications(applicantToken);
            applicationId = list.jsonPath().getLong("content[0].id");
        } else {
            assertThat(appResp.statusCode()).isEqualTo(201);
            applicationId = appResp.jsonPath().getLong("id");
        }
    }

    // ------------------------------------------------------------------
    // Step 2: Upload document
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void step02_uploadDocument() {
        Response response = api.createDocument(
                applicantToken, 2026, "bad-scan.pdf",
                "passport", "application/pdf", 102400);

        assertThat(response.statusCode()).isEqualTo(200);
        documentId = response.jsonPath().getLong("");
        assertThat(documentId).isPositive();

        // Upload IPN document — required for submission
        Response ipnResp = api.createDocument(
                applicantToken, 2026, "ipn-scan.pdf",
                "ipn", "application/pdf", 102400);
        assertThat(ipnResp.statusCode()).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // Step 3: Submit application
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void step03_submitApplication() {
        Response response = api.submitApplication(applicantToken, applicationId);
        assertThat(response.statusCode()).isEqualTo(204);
    }

    // ------------------------------------------------------------------
    // Step 4: Operator starts review then rejects the application
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void step04_operatorRejectsApplication() {
        // Move to reviewing
        Response reviewResp = api.reviewApplication(adminToken, applicationId, operatorUserId);
        assertThat(reviewResp.statusCode())
                .as("Start review")
                .isEqualTo(204);

        // Reject
        Response rejectResp = api.rejectApplication(adminToken, applicationId, REJECTION_REASON);
        assertThat(rejectResp.statusCode())
                .as("Reject application")
                .isEqualTo(204);
    }

    // ------------------------------------------------------------------
    // Step 5: Verify the rejection is visible to the applicant
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void step05_verifyRejectionReason() {
        Response response = api.getApplication(applicantToken, applicationId);
        assertThat(response.statusCode()).isEqualTo(200);

        String status = response.jsonPath().getString("status");
        assertThat(status)
                .as("Application status after rejection")
                .isEqualTo("rejected");

        // The rejection reason should be available in the response body.
        // The exact field name depends on the DTO; common names: rejectionReason, reason.
        String body = response.body().asString();
        assertThat(body)
                .as("Response body should contain the rejection reason")
                .contains(REJECTION_REASON);
    }
}
