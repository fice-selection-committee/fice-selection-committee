package edu.kpi.fice.e2e.flow;

import edu.kpi.fice.e2e.AbstractE2ETest;
import edu.kpi.fice.e2e.fixture.ApiClient;
import edu.kpi.fice.e2e.fixture.TestUsers;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test that exercises the complete admission workflow across
 * identity-service, admission-service, and documents-service.
 * <p>
 * Steps are executed in order; each step stores IDs / tokens that
 * subsequent steps depend on.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullAdmissionFlowE2ETest extends AbstractE2ETest {

    private static final ApiClient api = new ApiClient(
            IDENTITY_URL, ADMISSION_URL, DOCUMENTS_URL, ENVIRONMENT_URL);

    // Shared state between ordered test methods
    static String adminToken;
    static Long adminUserId;
    static String applicantToken;
    static Long applicantUserId;
    static String operatorToken;
    static Long operatorUserId;
    static Long applicationId;
    static Long documentId;
    static Long contractId;
    static Long groupId;
    static Long orderId;

    // Reference data created during setup
    static Long facultyId;
    static Long cathedraId;
    static Long educationalProgramId;

    // ------------------------------------------------------------------
    // Step 1-3: Register & login users
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void step01_registerApplicant() {
        Response response = api.register(
                TestUsers.APPLICANT_FIRST_NAME,
                TestUsers.APPLICANT_LAST_NAME,
                TestUsers.APPLICANT_EMAIL,
                TestUsers.APPLICANT_PASSWORD);

        // 201 if new, 409 if already exists from a previous run — both acceptable
        assertThat(response.statusCode())
                .as("Register applicant should succeed or conflict")
                .isIn(201, 409);
    }

    @Test
    @Order(2)
    void step02_loginAsApplicant() {
        Response response = api.login(TestUsers.APPLICANT_EMAIL, TestUsers.APPLICANT_PASSWORD);

        assertThat(response.statusCode())
                .as("Applicant login")
                .isEqualTo(200);

        applicantToken = response.jsonPath().getString("accessToken");
        assertThat(applicantToken).isNotBlank();

        // Fetch user id
        Response userResponse = api.getCurrentUser(applicantToken);
        assertThat(userResponse.statusCode()).isEqualTo(200);
        applicantUserId = userResponse.jsonPath().getLong("id");
        assertThat(applicantUserId).isPositive();
    }

    @Test
    @Order(3)
    void step03_loginAsAdmin() {
        // Admin user is expected to be pre-seeded in the database.
        // If the environment seeds an admin differently, adjust TestUsers constants.
        Response response = api.login(TestUsers.ADMIN_EMAIL, TestUsers.ADMIN_PASSWORD);

        assertThat(response.statusCode())
                .as("Admin login — make sure an admin user is seeded")
                .isEqualTo(200);

        adminToken = response.jsonPath().getString("accessToken");
        assertThat(adminToken).isNotBlank();

        Response userResponse = api.getCurrentUser(adminToken);
        assertThat(userResponse.statusCode()).isEqualTo(200);
        adminUserId = userResponse.jsonPath().getLong("id");
        assertThat(adminUserId).isPositive();
    }

    // ------------------------------------------------------------------
    // Step 4: Set up reference data (faculty, cathedra, program)
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void step04_createReferenceData() {
        // Faculty — create or fetch existing
        Response facultyResp = api.createFaculty(adminToken, "E2E Test Faculty");
        if (facultyResp.statusCode() == 409) {
            Response list = api.listFaculties(adminToken);
            facultyId = list.jsonPath().getLong("content.find { it.name == 'E2E Test Faculty' }.id");
        } else {
            assertThat(facultyResp.statusCode()).as("Create faculty").isIn(201, 200);
            facultyId = facultyResp.jsonPath().getLong("id");
        }
        assertThat(facultyId).isPositive();

        // Cathedra — create or fetch existing
        Response cathedraResp = api.createCathedra(adminToken, "E2E Test Cathedra", facultyId);
        if (cathedraResp.statusCode() == 409) {
            Response list = api.listCathedras(adminToken, facultyId);
            cathedraId = list.jsonPath().getLong("content.find { it.name == 'E2E Test Cathedra' }.id");
        } else {
            assertThat(cathedraResp.statusCode()).as("Create cathedra").isIn(201, 200);
            cathedraId = cathedraResp.jsonPath().getLong("id");
        }
        assertThat(cathedraId).isPositive();

        // Educational program — create or fetch existing
        Response epResp = api.createEducationalProgram(adminToken, "E2E Test Program", cathedraId);
        if (epResp.statusCode() == 409) {
            Response list = api.listEducationalPrograms(adminToken, cathedraId);
            educationalProgramId = list.jsonPath().getLong("content.find { it.name == 'E2E Test Program' }.id");
        } else {
            assertThat(epResp.statusCode()).as("Create educational program").isIn(201, 200);
            educationalProgramId = epResp.jsonPath().getLong("id");
        }
        assertThat(educationalProgramId).isPositive();
    }

    // ------------------------------------------------------------------
    // Step 5: Create application
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void step05_createApplication() {
        Response response = api.createApplication(
                applicantToken, applicantUserId, true, "draft");

        if (response.statusCode() == 409) {
            // Application already exists from a previous run — fetch it
            Response list = api.listApplications(applicantToken);
            applicationId = list.jsonPath().getLong("content[0].id");
        } else {
            assertThat(response.statusCode()).as("Create application").isEqualTo(201);
            applicationId = response.jsonPath().getLong("id");
        }
        assertThat(applicationId).isPositive();
    }

    // ------------------------------------------------------------------
    // Step 6: Upload document
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void step06_uploadDocument() {
        // Upload passport document
        Response passportResp = api.createDocument(
                applicantToken, 2026, "passport-scan.pdf",
                "passport", "application/pdf", 204800);

        assertThat(passportResp.statusCode())
                .as("Create passport document metadata")
                .isEqualTo(200);

        documentId = passportResp.jsonPath().getLong("");
        assertThat(documentId).isPositive();

        // Get a presigned upload URL (we won't actually upload a file
        // in the E2E test, but we verify the endpoint works)
        Response presignResp = api.presignUpload(applicantToken, documentId);
        assertThat(presignResp.statusCode())
                .as("Presign upload URL")
                .isEqualTo(200);
        assertThat(presignResp.body().asString()).isNotBlank();

        // Upload IPN (tax ID) document — required for submission
        Response ipnResp = api.createDocument(
                applicantToken, 2026, "ipn-scan.pdf",
                "ipn", "application/pdf", 102400);
        assertThat(ipnResp.statusCode())
                .as("Create IPN document metadata")
                .isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // Step 7: Submit application
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void step07_submitApplication() {
        Response response = api.submitApplication(applicantToken, applicationId);
        assertThat(response.statusCode())
                .as("Submit application")
                .isEqualTo(204);
    }

    // ------------------------------------------------------------------
    // Step 8: Verify submitted status (possibly auto-assigned to reviewing)
    // ------------------------------------------------------------------

    @Test
    @Order(8)
    void step08_verifyAutoAssignment() {
        // The system may auto-assign an operator, moving the status to 'reviewing'.
        // Poll for up to 30 seconds.
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Response response = api.getApplication(applicantToken, applicationId);
                    assertThat(response.statusCode()).isEqualTo(200);
                    String status = response.jsonPath().getString("status");
                    assertThat(status)
                            .as("Application should be submitted or reviewing")
                            .isIn("submitted", "reviewing");
                });
    }

    // ------------------------------------------------------------------
    // Step 9: Register & login as operator
    // ------------------------------------------------------------------

    @Test
    @Order(9)
    void step09_setupOperator() {
        // Register operator
        Response regResp = api.register(
                TestUsers.OPERATOR_FIRST_NAME,
                TestUsers.OPERATOR_LAST_NAME,
                TestUsers.OPERATOR_EMAIL,
                TestUsers.OPERATOR_PASSWORD);
        assertThat(regResp.statusCode())
                .as("Register operator")
                .isIn(201, 409);

        // Login operator
        Response loginResp = api.login(TestUsers.OPERATOR_EMAIL, TestUsers.OPERATOR_PASSWORD);
        assertThat(loginResp.statusCode()).isEqualTo(200);
        operatorToken = loginResp.jsonPath().getString("accessToken");
        assertThat(operatorToken).isNotBlank();

        Response userResp = api.getCurrentUser(operatorToken);
        assertThat(userResp.statusCode()).isEqualTo(200);
        operatorUserId = userResp.jsonPath().getLong("id");

        // Promote to OPERATOR role via admin
        Response roleResp = api.changeUserRole(adminToken, operatorUserId, "ROLE_OPERATOR");
        assertThat(roleResp.statusCode())
                .as("Change operator role")
                .isEqualTo(200);

        // Re-login to get token with new authorities
        loginResp = api.login(TestUsers.OPERATOR_EMAIL, TestUsers.OPERATOR_PASSWORD);
        assertThat(loginResp.statusCode()).isEqualTo(200);
        operatorToken = loginResp.jsonPath().getString("accessToken");
    }

    // ------------------------------------------------------------------
    // Step 10: Operator starts review
    // ------------------------------------------------------------------

    @Test
    @Order(10)
    void step10_startReview() {
        // Ensure the application is in 'submitted' state first; if auto-assignment
        // already moved it to 'reviewing', this step can be skipped.
        Response getResp = api.getApplication(adminToken, applicationId);
        String currentStatus = getResp.jsonPath().getString("status");

        if ("submitted".equals(currentStatus)) {
            Response response = api.reviewApplication(adminToken, applicationId, operatorUserId);
            assertThat(response.statusCode())
                    .as("Start review")
                    .isEqualTo(204);
        }

        // Verify status is now 'reviewing'
        Response verify = api.getApplication(adminToken, applicationId);
        assertThat(verify.jsonPath().getString("status")).isEqualTo("reviewing");
    }

    // ------------------------------------------------------------------
    // Step 11: Accept application
    // ------------------------------------------------------------------

    @Test
    @Order(11)
    void step11_acceptApplication() {
        Response response = api.acceptApplication(adminToken, applicationId);
        assertThat(response.statusCode())
                .as("Accept application")
                .isEqualTo(204);

        Response verify = api.getApplication(adminToken, applicationId);
        assertThat(verify.jsonPath().getString("status")).isEqualTo("accepted");
    }

    // ------------------------------------------------------------------
    // Step 12: Create contract
    // ------------------------------------------------------------------

    @Test
    @Order(12)
    void step12_createContract() {
        Response response = api.createContract(adminToken, applicationId, "budget");
        assertThat(response.statusCode())
                .as("Create contract draft")
                .isEqualTo(201);

        contractId = response.jsonPath().getLong("id");
        assertThat(contractId).isPositive();
    }

    // ------------------------------------------------------------------
    // Step 13: Register contract
    // ------------------------------------------------------------------

    @Test
    @Order(13)
    void step13_registerContract() {
        Response response = api.registerContract(adminToken, contractId, "E2E-CONTRACT-001");
        assertThat(response.statusCode())
                .as("Register contract")
                .isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // Step 14: Create group
    // ------------------------------------------------------------------

    @Test
    @Order(14)
    void step14_createGroup() {
        Response response = api.createGroup(adminToken, 2026, "E2E-GRP-01", educationalProgramId);
        if (response.statusCode() == 409) {
            Response list = api.listGroups(adminToken, educationalProgramId, 2026);
            groupId = list.jsonPath().getLong("content[0].id");
        } else {
            assertThat(response.statusCode()).as("Create group").isEqualTo(201);
            groupId = response.jsonPath().getLong("id");
        }
        assertThat(groupId).isPositive();
    }

    // ------------------------------------------------------------------
    // Step 15: Assign to group
    // ------------------------------------------------------------------

    @Test
    @Order(15)
    void step15_assignToGroup() {
        Response response = api.assignToGroup(adminToken, applicationId, groupId);
        assertThat(response.statusCode())
                .as("Assign application to group")
                .isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // Step 16: Create enrollment order
    // ------------------------------------------------------------------

    @Test
    @Order(16)
    void step16_createEnrollmentOrder() {
        Response response = api.createOrder(
                adminToken, adminUserId, "enrollment", List.of(applicationId));
        assertThat(response.statusCode())
                .as("Create enrollment order")
                .isEqualTo(201);

        orderId = response.jsonPath().getLong("id");
        assertThat(orderId).isPositive();
    }

    // ------------------------------------------------------------------
    // Step 17: Sign enrollment order
    // ------------------------------------------------------------------

    @Test
    @Order(17)
    void step17_signOrder() {
        Response response = api.signOrder(adminToken, adminUserId, orderId);
        assertThat(response.statusCode())
                .as("Sign enrollment order")
                .isEqualTo(200);

        String orderStatus = response.jsonPath().getString("status");
        assertThat(orderStatus).isEqualTo("signed");
    }

    // ------------------------------------------------------------------
    // Step 18: Verify enrolled status
    // ------------------------------------------------------------------

    @Test
    @Order(18)
    void step18_verifyEnrolledStatus() {
        // After the order is signed the application should transition to 'enrolled'.
        // This may happen asynchronously, so we poll.
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Response response = api.getApplication(adminToken, applicationId);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.jsonPath().getString("status"))
                            .as("Application should be enrolled after order is signed")
                            .isEqualTo("enrolled");
                });
    }
}
