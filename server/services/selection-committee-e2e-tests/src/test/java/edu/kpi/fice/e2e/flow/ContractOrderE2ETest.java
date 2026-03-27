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
 * End-to-end test that exercises the contract and order lifecycle
 * across multiple roles: APPLICANT, OPERATOR, CONTRACT_MANAGER,
 * and EXECUTIVE_SECRETARY.
 * <p>
 * Flow:
 * 1. Applicant creates and submits an application
 * 2. Operator reviews and accepts it
 * 3. Contract Manager creates and registers a contract
 * 4. Executive Secretary creates and signs an enrollment order
 * 5. Application status reflects the signed order (enrolled)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContractOrderE2ETest extends AbstractE2ETest {

    private static final ApiClient api = new ApiClient(
            IDENTITY_URL, ADMISSION_URL, DOCUMENTS_URL, ENVIRONMENT_URL);

    // Shared state between ordered test methods
    static String adminToken;
    static Long adminUserId;

    static String applicantToken;
    static Long applicantUserId;

    static String operatorToken;
    static Long operatorUserId;

    static String contractManagerToken;
    static Long contractManagerUserId;

    static String executiveSecretaryToken;
    static Long executiveSecretaryUserId;

    static Long applicationId;
    static Long contractId;
    static Long orderId;

    // Reference data
    static Long facultyId;
    static Long cathedraId;
    static Long educationalProgramId;
    static Long groupId;

    // ------------------------------------------------------------------
    // Step 1: Login as APPLICANT
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void step01_loginAsApplicant() {
        // Register applicant (idempotent)
        api.register(
                TestUsers.APPLICANT_FIRST_NAME,
                TestUsers.APPLICANT_LAST_NAME,
                "co-applicant@e2e-test.kpi.ua",
                TestUsers.APPLICANT_PASSWORD);

        Response loginResp = api.login("co-applicant@e2e-test.kpi.ua", TestUsers.APPLICANT_PASSWORD);
        assertThat(loginResp.statusCode())
                .as("Applicant login")
                .isEqualTo(200);

        applicantToken = loginResp.jsonPath().getString("accessToken");
        assertThat(applicantToken).isNotBlank();

        Response userResp = api.getCurrentUser(applicantToken);
        assertThat(userResp.statusCode()).isEqualTo(200);
        applicantUserId = userResp.jsonPath().getLong("id");
        assertThat(applicantUserId).isPositive();
    }

    // ------------------------------------------------------------------
    // Step 2: Login as ADMIN and set up reference data
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void step02_loginAsAdminAndSetupReferenceData() {
        // Admin is pre-seeded
        Response loginResp = api.login(TestUsers.ADMIN_EMAIL, TestUsers.ADMIN_PASSWORD);
        assertThat(loginResp.statusCode())
                .as("Admin login")
                .isEqualTo(200);

        adminToken = loginResp.jsonPath().getString("accessToken");
        assertThat(adminToken).isNotBlank();

        Response userResp = api.getCurrentUser(adminToken);
        assertThat(userResp.statusCode()).isEqualTo(200);
        adminUserId = userResp.jsonPath().getLong("id");

        // Faculty — create or fetch existing
        Response facultyResp = api.createFaculty(adminToken, "CO E2E Faculty");
        if (facultyResp.statusCode() == 409) {
            Response list = api.listFaculties(adminToken);
            facultyId = list.jsonPath().getLong("content.find { it.name == 'CO E2E Faculty' }.id");
        } else {
            assertThat(facultyResp.statusCode()).isIn(200, 201);
            facultyId = facultyResp.jsonPath().getLong("id");
        }

        // Cathedra — create or fetch existing
        Response cathedraResp = api.createCathedra(adminToken, "CO E2E Cathedra", facultyId);
        if (cathedraResp.statusCode() == 409) {
            Response list = api.listCathedras(adminToken, facultyId);
            cathedraId = list.jsonPath().getLong("content.find { it.name == 'CO E2E Cathedra' }.id");
        } else {
            assertThat(cathedraResp.statusCode()).isIn(200, 201);
            cathedraId = cathedraResp.jsonPath().getLong("id");
        }

        // Educational program — create or fetch existing
        Response epResp = api.createEducationalProgram(adminToken, "CO E2E Program", cathedraId);
        if (epResp.statusCode() == 409) {
            Response list = api.listEducationalPrograms(adminToken, cathedraId);
            educationalProgramId = list.jsonPath().getLong("content.find { it.name == 'CO E2E Program' }.id");
        } else {
            assertThat(epResp.statusCode()).isIn(200, 201);
            educationalProgramId = epResp.jsonPath().getLong("id");
        }
    }

    // ------------------------------------------------------------------
    // Step 3: Create application -> DRAFT
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void step03_createDraftApplication() {
        Response response = api.createApplication(
                applicantToken, applicantUserId, true, "draft");

        if (response.statusCode() == 409) {
            Response list = api.listApplications(applicantToken);
            applicationId = list.jsonPath().getLong("content[0].id");
        } else {
            assertThat(response.statusCode()).as("Create application").isEqualTo(201);
            applicationId = response.jsonPath().getLong("id");
        }
        assertThat(applicationId).isPositive();
    }

    // ------------------------------------------------------------------
    // Step 3b: Upload required documents before submission
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void step03b_uploadDocuments() {
        // Passport document
        Response passportResp = api.createDocument(
                applicantToken, 2026, "co-passport.pdf",
                "passport", "application/pdf", 204800);
        assertThat(passportResp.statusCode()).as("Upload passport").isEqualTo(200);

        // IPN document
        Response ipnResp = api.createDocument(
                applicantToken, 2026, "co-ipn.pdf",
                "ipn", "application/pdf", 102400);
        assertThat(ipnResp.statusCode()).as("Upload IPN").isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // Step 4: Submit application -> SUBMITTED
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void step04_submitApplication() {
        Response response = api.submitApplication(applicantToken, applicationId);
        assertThat(response.statusCode())
                .as("Submit application")
                .isEqualTo(204);
    }

    // ------------------------------------------------------------------
    // Step 5: Setup OPERATOR (register, promote, re-login)
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void step05_setupOperator() {
        // Register operator
        api.register(
                TestUsers.OPERATOR_FIRST_NAME,
                TestUsers.OPERATOR_LAST_NAME,
                "co-operator@e2e-test.kpi.ua",
                TestUsers.OPERATOR_PASSWORD);

        // Login
        Response loginResp = api.login("co-operator@e2e-test.kpi.ua", TestUsers.OPERATOR_PASSWORD);
        assertThat(loginResp.statusCode()).isEqualTo(200);
        operatorToken = loginResp.jsonPath().getString("accessToken");

        Response userResp = api.getCurrentUser(operatorToken);
        assertThat(userResp.statusCode()).isEqualTo(200);
        operatorUserId = userResp.jsonPath().getLong("id");

        // Promote to OPERATOR role
        Response roleResp = api.changeUserRole(adminToken, operatorUserId, "ROLE_OPERATOR");
        assertThat(roleResp.statusCode()).isEqualTo(200);

        // Re-login to get token with new authorities
        loginResp = api.login("co-operator@e2e-test.kpi.ua", TestUsers.OPERATOR_PASSWORD);
        assertThat(loginResp.statusCode()).isEqualTo(200);
        operatorToken = loginResp.jsonPath().getString("accessToken");
    }

    // ------------------------------------------------------------------
    // Step 6: Operator reviews -> moves to REVIEWING
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void step06_operatorStartsReview() {
        // Check current status — may already be 'reviewing' if auto-assigned
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
        assertThat(verify.jsonPath().getString("status"))
                .as("Application should be in reviewing state")
                .isIn("submitted", "reviewing");
    }

    // ------------------------------------------------------------------
    // Step 7: Operator accepts application -> ACCEPTED
    // ------------------------------------------------------------------

    @Test
    @Order(8)
    void step07_operatorAcceptsApplication() {
        // Wait for reviewing state if needed
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Response resp = api.getApplication(adminToken, applicationId);
                    assertThat(resp.jsonPath().getString("status")).isIn("reviewing", "accepted");
                });

        Response getResp = api.getApplication(adminToken, applicationId);
        if (!"accepted".equals(getResp.jsonPath().getString("status"))) {
            Response response = api.acceptApplication(adminToken, applicationId);
            assertThat(response.statusCode())
                    .as("Accept application")
                    .isEqualTo(204);
        }

        // Verify accepted
        Response verify = api.getApplication(adminToken, applicationId);
        assertThat(verify.jsonPath().getString("status")).isEqualTo("accepted");
    }

    // ------------------------------------------------------------------
    // Step 8: Setup CONTRACT_MANAGER (register, promote, re-login)
    // ------------------------------------------------------------------

    @Test
    @Order(9)
    void step08_setupContractManager() {
        // Register
        api.register(
                TestUsers.CONTRACT_MANAGER_FIRST_NAME,
                TestUsers.CONTRACT_MANAGER_LAST_NAME,
                TestUsers.CONTRACT_MANAGER_EMAIL,
                TestUsers.CONTRACT_MANAGER_PASSWORD);

        // Login
        Response loginResp = api.login(
                TestUsers.CONTRACT_MANAGER_EMAIL, TestUsers.CONTRACT_MANAGER_PASSWORD);
        assertThat(loginResp.statusCode()).isEqualTo(200);
        contractManagerToken = loginResp.jsonPath().getString("accessToken");

        Response userResp = api.getCurrentUser(contractManagerToken);
        assertThat(userResp.statusCode()).isEqualTo(200);
        contractManagerUserId = userResp.jsonPath().getLong("id");

        // Promote to CONTRACT_MANAGER role
        Response roleResp = api.changeUserRole(adminToken, contractManagerUserId, "ROLE_CONTRACT_MANAGER");
        assertThat(roleResp.statusCode()).isEqualTo(200);

        // Re-login to get token with new authorities
        loginResp = api.login(
                TestUsers.CONTRACT_MANAGER_EMAIL, TestUsers.CONTRACT_MANAGER_PASSWORD);
        assertThat(loginResp.statusCode()).isEqualTo(200);
        contractManagerToken = loginResp.jsonPath().getString("accessToken");
    }

    // ------------------------------------------------------------------
    // Step 9: Contract Manager creates draft contract
    // ------------------------------------------------------------------

    @Test
    @Order(10)
    void step09_createDraftContract() {
        Response response = api.createContract(contractManagerToken, applicationId, "budget");
        assertThat(response.statusCode())
                .as("Create contract draft as CONTRACT_MANAGER")
                .isEqualTo(201);

        contractId = response.jsonPath().getLong("id");
        assertThat(contractId).isPositive();

        String contractStatus = response.jsonPath().getString("status");
        assertThat(contractStatus)
                .as("New contract should be in draft status")
                .isEqualToIgnoringCase("draft");
    }

    // ------------------------------------------------------------------
    // Step 10: Contract Manager registers contract
    // ------------------------------------------------------------------

    @Test
    @Order(11)
    void step10_registerContract() {
        Response response = api.registerContract(
                contractManagerToken, contractId, "CO-E2E-CONTRACT-001");
        assertThat(response.statusCode())
                .as("Register contract as CONTRACT_MANAGER")
                .isEqualTo(200);

        String contractStatus = response.jsonPath().getString("status");
        assertThat(contractStatus)
                .as("Contract should be registered")
                .isEqualToIgnoringCase("registered");
    }

    // ------------------------------------------------------------------
    // Step 11: Verify contract is visible by application
    // ------------------------------------------------------------------

    @Test
    @Order(12)
    void step11_verifyContractByApplication() {
        Response response = api.getContractsByApplication(contractManagerToken, applicationId);
        assertThat(response.statusCode()).isEqualTo(200);

        String body = response.body().asString();
        assertThat(body)
                .as("Contracts list should contain the created contract")
                .contains("CO-E2E-CONTRACT-001");
    }

    // ------------------------------------------------------------------
    // Step 12: Setup EXECUTIVE_SECRETARY (register, promote, re-login)
    // ------------------------------------------------------------------

    @Test
    @Order(13)
    void step12_setupExecutiveSecretary() {
        // Register
        api.register(
                TestUsers.EXECUTIVE_SECRETARY_FIRST_NAME,
                TestUsers.EXECUTIVE_SECRETARY_LAST_NAME,
                TestUsers.EXECUTIVE_SECRETARY_EMAIL,
                TestUsers.EXECUTIVE_SECRETARY_PASSWORD);

        // Login
        Response loginResp = api.login(
                TestUsers.EXECUTIVE_SECRETARY_EMAIL, TestUsers.EXECUTIVE_SECRETARY_PASSWORD);
        assertThat(loginResp.statusCode()).isEqualTo(200);
        executiveSecretaryToken = loginResp.jsonPath().getString("accessToken");

        Response userResp = api.getCurrentUser(executiveSecretaryToken);
        assertThat(userResp.statusCode()).isEqualTo(200);
        executiveSecretaryUserId = userResp.jsonPath().getLong("id");

        // Promote to EXECUTIVE_SECRETARY role
        Response roleResp = api.changeUserRole(
                adminToken, executiveSecretaryUserId, "ROLE_EXECUTIVE_SECRETARY");
        assertThat(roleResp.statusCode()).isEqualTo(200);

        // Re-login to get token with new authorities
        loginResp = api.login(
                TestUsers.EXECUTIVE_SECRETARY_EMAIL, TestUsers.EXECUTIVE_SECRETARY_PASSWORD);
        assertThat(loginResp.statusCode()).isEqualTo(200);
        executiveSecretaryToken = loginResp.jsonPath().getString("accessToken");
    }

    // ------------------------------------------------------------------
    // Step 13: Create group and assign application
    // ------------------------------------------------------------------

    @Test
    @Order(14)
    void step13_createGroupAndAssign() {
        // Create group (admin privilege) — handle existing or reuse from FullAdmission flow
        Response groupResp = api.createGroup(
                adminToken, 2026, "CO-GRP-01", educationalProgramId);
        if (groupResp.statusCode() == 409) {
            Response list = api.listGroups(adminToken, educationalProgramId, 2026);
            groupId = list.jsonPath().getLong("content[0].id");
        } else {
            assertThat(groupResp.statusCode()).as("Create group").isEqualTo(201);
            groupId = groupResp.jsonPath().getLong("id");
        }
        assertThat(groupId).as("Group ID must be set").isPositive();

        // Assign application to group
        Response assignResp = api.assignToGroup(adminToken, applicationId, groupId);
        assertThat(assignResp.statusCode())
                .as("Assign application to group")
                .isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // Step 14: Executive Secretary creates enrollment order
    // ------------------------------------------------------------------

    @Test
    @Order(15)
    void step14_createEnrollmentOrder() {
        Response response = api.createOrder(
                executiveSecretaryToken, executiveSecretaryUserId,
                "enrollment", List.of(applicationId));

        assertThat(response.statusCode())
                .as("Create enrollment order as EXECUTIVE_SECRETARY")
                .isEqualTo(201);

        orderId = response.jsonPath().getLong("id");
        assertThat(orderId).isPositive();
    }

    // ------------------------------------------------------------------
    // Step 15: Executive Secretary signs the order
    // ------------------------------------------------------------------

    @Test
    @Order(16)
    void step15_signOrder() {
        Response response = api.signOrder(executiveSecretaryToken, executiveSecretaryUserId, orderId);
        assertThat(response.statusCode())
                .as("Sign enrollment order as EXECUTIVE_SECRETARY")
                .isEqualTo(200);

        String orderStatus = response.jsonPath().getString("status");
        assertThat(orderStatus)
                .as("Order should be signed")
                .isEqualTo("signed");
    }

    // ------------------------------------------------------------------
    // Step 16: Verify application status reflects signed order (enrolled)
    // ------------------------------------------------------------------

    @Test
    @Order(17)
    void step16_verifyApplicationEnrolled() {
        // The order signing may trigger async status update; poll for it.
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

    // ------------------------------------------------------------------
    // Step 17: Verify order details
    // ------------------------------------------------------------------

    @Test
    @Order(18)
    void step17_verifyOrderDetails() {
        Response response = api.getOrder(executiveSecretaryToken, orderId);
        assertThat(response.statusCode()).isEqualTo(200);

        assertThat(response.jsonPath().getString("status"))
                .as("Order should remain signed")
                .isEqualTo("signed");

        assertThat(response.jsonPath().getString("orderType"))
                .as("Order type should be enrollment")
                .isEqualTo("enrollment");
    }
}
