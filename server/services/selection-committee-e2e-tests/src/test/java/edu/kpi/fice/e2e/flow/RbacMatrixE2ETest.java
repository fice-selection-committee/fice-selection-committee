package edu.kpi.fice.e2e.flow;

import edu.kpi.fice.e2e.AbstractE2ETest;
import edu.kpi.fice.e2e.fixture.ApiClient;
import edu.kpi.fice.e2e.fixture.TestUsers;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the RBAC (Role-Based Access Control) matrix by verifying that each role
 * can or cannot access specific endpoints.
 * <p>
 * Each test is independent — no shared mutable state beyond the tokens
 * obtained in {@link #loginAllRoles()}.
 */
@Tag("smoke")
class RbacMatrixE2ETest extends AbstractE2ETest {

    private static final ApiClient api = new ApiClient(
            IDENTITY_URL, ADMISSION_URL, DOCUMENTS_URL, ENVIRONMENT_URL);

    static String applicantToken;
    static Long applicantUserId;
    static String operatorToken;
    static Long operatorUserId;
    static String adminToken;
    static Long adminUserId;
    static String contractManagerToken;
    static Long contractManagerUserId;
    static String executiveSecretaryToken;
    static Long executiveSecretaryUserId;

    @BeforeAll
    static void loginAllRoles() {
        // --- Admin (pre-seeded) ---
        Response adminLogin = api.login(TestUsers.ADMIN_EMAIL, TestUsers.ADMIN_PASSWORD);
        assertThat(adminLogin.statusCode())
                .as("Admin login")
                .isEqualTo(200);
        adminToken = adminLogin.jsonPath().getString("accessToken");

        Response adminUser = api.getCurrentUser(adminToken);
        adminUserId = adminUser.jsonPath().getLong("id");

        // --- Applicant ---
        api.register(
                TestUsers.APPLICANT_FIRST_NAME,
                TestUsers.APPLICANT_LAST_NAME,
                "rbac-applicant@e2e-test.kpi.ua",
                TestUsers.APPLICANT_PASSWORD);

        Response applicantLogin = api.login("rbac-applicant@e2e-test.kpi.ua", TestUsers.APPLICANT_PASSWORD);
        assertThat(applicantLogin.statusCode()).isEqualTo(200);
        applicantToken = applicantLogin.jsonPath().getString("accessToken");

        Response applicantUser = api.getCurrentUser(applicantToken);
        applicantUserId = applicantUser.jsonPath().getLong("id");

        // --- Operator ---
        api.register(
                TestUsers.OPERATOR_FIRST_NAME,
                TestUsers.OPERATOR_LAST_NAME,
                "rbac-operator@e2e-test.kpi.ua",
                TestUsers.OPERATOR_PASSWORD);

        Response opLogin = api.login("rbac-operator@e2e-test.kpi.ua", TestUsers.OPERATOR_PASSWORD);
        assertThat(opLogin.statusCode()).isEqualTo(200);
        operatorToken = opLogin.jsonPath().getString("accessToken");

        Response opUser = api.getCurrentUser(operatorToken);
        operatorUserId = opUser.jsonPath().getLong("id");

        // Promote to OPERATOR role
        api.changeUserRole(adminToken, operatorUserId, "ROLE_OPERATOR");

        // Re-login to pick up new authorities
        opLogin = api.login("rbac-operator@e2e-test.kpi.ua", TestUsers.OPERATOR_PASSWORD);
        operatorToken = opLogin.jsonPath().getString("accessToken");

        // --- Contract Manager ---
        api.register(
                TestUsers.CONTRACT_MANAGER_FIRST_NAME,
                TestUsers.CONTRACT_MANAGER_LAST_NAME,
                "rbac-contract-mgr@e2e-test.kpi.ua",
                TestUsers.CONTRACT_MANAGER_PASSWORD);

        Response cmLogin = api.login("rbac-contract-mgr@e2e-test.kpi.ua", TestUsers.CONTRACT_MANAGER_PASSWORD);
        assertThat(cmLogin.statusCode()).isEqualTo(200);
        contractManagerToken = cmLogin.jsonPath().getString("accessToken");

        Response cmUser = api.getCurrentUser(contractManagerToken);
        contractManagerUserId = cmUser.jsonPath().getLong("id");

        api.changeUserRole(adminToken, contractManagerUserId, "ROLE_CONTRACT_MANAGER");

        cmLogin = api.login("rbac-contract-mgr@e2e-test.kpi.ua", TestUsers.CONTRACT_MANAGER_PASSWORD);
        contractManagerToken = cmLogin.jsonPath().getString("accessToken");

        // --- Executive Secretary ---
        api.register(
                TestUsers.EXECUTIVE_SECRETARY_FIRST_NAME,
                TestUsers.EXECUTIVE_SECRETARY_LAST_NAME,
                "rbac-exec-secretary@e2e-test.kpi.ua",
                TestUsers.EXECUTIVE_SECRETARY_PASSWORD);

        Response esLogin = api.login("rbac-exec-secretary@e2e-test.kpi.ua", TestUsers.EXECUTIVE_SECRETARY_PASSWORD);
        assertThat(esLogin.statusCode()).isEqualTo(200);
        executiveSecretaryToken = esLogin.jsonPath().getString("accessToken");

        Response esUser = api.getCurrentUser(executiveSecretaryToken);
        executiveSecretaryUserId = esUser.jsonPath().getLong("id");

        api.changeUserRole(adminToken, executiveSecretaryUserId, "ROLE_EXECUTIVE_SECRETARY");

        esLogin = api.login("rbac-exec-secretary@e2e-test.kpi.ua", TestUsers.EXECUTIVE_SECRETARY_PASSWORD);
        executiveSecretaryToken = esLogin.jsonPath().getString("accessToken");
    }

    // =========================================================================
    // Applications — list all
    // =========================================================================

    @Test
    void applicant_canListOwnApplications() {
        // APPLICANT has access to GET /api/v1/admissions (returns only own)
        Response response = api.listApplications(applicantToken);
        assertThat(response.statusCode())
                .as("Applicant can list applications")
                .isEqualTo(200);
    }

    @Test
    void operator_canListAllApplications() {
        Response response = api.listApplications(operatorToken);
        assertThat(response.statusCode())
                .as("Operator can list applications")
                .isEqualTo(200);
    }

    // =========================================================================
    // Applications — delete (ADMIN only)
    // =========================================================================

    @Test
    void applicant_cannotDeleteApplication() {
        // Create an application first so we have an ID to try deleting
        Response createResp = api.createApplication(applicantToken, applicantUserId, true, "draft");
        Long appId;
        if (createResp.statusCode() == 409) {
            appId = api.listApplications(applicantToken).jsonPath().getLong("content[0].id");
        } else {
            assertThat(createResp.statusCode()).isEqualTo(201);
            appId = createResp.jsonPath().getLong("id");
        }

        Response deleteResp = api.deleteApplication(applicantToken, appId);
        assertThat(deleteResp.statusCode())
                .as("Applicant should NOT be able to delete applications")
                .isEqualTo(403);
    }

    @Test
    void admin_canDeleteApplication() {
        // Create via admin — use a fresh applicant to avoid 409
        Response createResp = api.createApplication(adminToken, adminUserId, true, "draft");
        Long appId;
        if (createResp.statusCode() == 409) {
            appId = api.listApplications(adminToken).jsonPath().getLong("content[0].id");
        } else {
            assertThat(createResp.statusCode()).isEqualTo(201);
            appId = createResp.jsonPath().getLong("id");
        }

        Response deleteResp = api.deleteApplication(adminToken, appId);
        assertThat(deleteResp.statusCode())
                .as("Admin should be able to delete applications")
                .isEqualTo(204);
    }

    // =========================================================================
    // Admin users endpoint
    // =========================================================================

    @Test
    void applicant_cannotAccessAdminUsers() {
        Response response = api.listUsers(applicantToken);
        assertThat(response.statusCode())
                .as("Applicant should NOT access admin users list")
                .isEqualTo(403);
    }

    @Test
    void admin_canAccessAdminUsers() {
        Response response = api.listUsers(adminToken);
        assertThat(response.statusCode())
                .as("Admin should access admin users list")
                .isEqualTo(200);
    }

    // =========================================================================
    // Faculties — create (ADMIN only)
    // =========================================================================

    @Test
    void applicant_cannotCreateFaculty() {
        Response response = api.createFaculty(applicantToken, "RBAC Test Faculty");
        assertThat(response.statusCode())
                .as("Applicant should NOT be able to create faculties")
                .isEqualTo(403);
    }

    @Test
    void admin_canCreateFaculty() {
        Response response = api.createFaculty(adminToken, "RBAC Admin Faculty");
        assertThat(response.statusCode())
                .as("Admin should be able to create faculties")
                .isIn(200, 201);
    }

    // =========================================================================
    // Documents — upload (APPLICANT can) and cross-user visibility
    // =========================================================================

    @Test
    void applicant_canUploadDocuments() {
        Response response = api.createDocument(
                applicantToken, 2026, "rbac-test.pdf",
                "passport", "application/pdf", 51200);
        assertThat(response.statusCode())
                .as("Applicant should be able to create document metadata")
                .isEqualTo(200);
    }

    @Test
    void operator_canViewAnyDocuments() {
        // Create a document as applicant
        Response createResp = api.createDocument(
                applicantToken, 2026, "operator-view-test.pdf",
                "passport", "application/pdf", 51200);
        assertThat(createResp.statusCode()).isEqualTo(200);
        Long docId = createResp.jsonPath().getLong("");

        // Operator should be able to view it
        Response viewResp = api.getDocument(operatorToken, docId);
        assertThat(viewResp.statusCode())
                .as("Operator should be able to view any document metadata")
                .isEqualTo(200);
    }

    @Test
    void applicant_cannotViewOthersDocuments() {
        // Attempt to list documents for the admin user
        Response response = api.getDocumentsByOwner(applicantToken, adminUserId);
        // This should either return 403, or return an empty list depending
        // on whether the service enforces ownership at the query level or
        // at the authorization level.
        if (response.statusCode() == 200) {
            // If 200, verify the list is empty (service filters by ownership)
            String body = response.body().asString();
            assertThat(body)
                    .as("Applicant should not see admin's documents")
                    .isIn("[]", "");
        } else {
            assertThat(response.statusCode())
                    .as("Applicant should be forbidden from seeing others' documents")
                    .isEqualTo(403);
        }
    }

    // =========================================================================
    // Contracts — create (admin/secretary roles only)
    // =========================================================================

    @Test
    void applicant_cannotCreateContract() {
        // Use a made-up application ID; authorization check should happen before validation
        Response response = api.createContract(applicantToken, 999999L, "budget");
        assertThat(response.statusCode())
                .as("Applicant should NOT be able to create contracts")
                .isEqualTo(403);
    }

    @Test
    void admin_canCreateContract() {
        // Create an application to reference (RBAC check — not business logic)
        Response appResp = api.createApplication(adminToken, adminUserId, true, "draft");
        Long appId;
        if (appResp.statusCode() == 409) {
            appId = api.listApplications(adminToken).jsonPath().getLong("content[0].id");
        } else {
            appId = appResp.jsonPath().getLong("id");
        }

        Response response = api.createContract(adminToken, appId, "budget");
        assertThat(response.statusCode())
                .as("Admin should be authorized to create contracts (not 403)")
                .isNotEqualTo(403);
    }

    // =========================================================================
    // Orders — create (admin/secretary roles only)
    // =========================================================================

    @Test
    void applicant_cannotCreateOrder() {
        Response response = api.createOrder(
                applicantToken, applicantUserId, "enrollment", java.util.List.of(999999L));
        assertThat(response.statusCode())
                .as("Applicant should NOT be able to create orders")
                .isEqualTo(403);
    }

    @Test
    void operator_cannotCreateOrder() {
        Response response = api.createOrder(
                operatorToken, operatorUserId, "enrollment", java.util.List.of(999999L));
        assertThat(response.statusCode())
                .as("Operator should NOT be able to create orders")
                .isEqualTo(403);
    }

    // =========================================================================
    // Group assignments — ADMIN only
    // =========================================================================

    @Test
    void applicant_cannotAssignToGroup() {
        Response response = api.assignToGroup(applicantToken, 999999L, 999999L);
        assertThat(response.statusCode())
                .as("Applicant should NOT be able to assign to groups")
                .isEqualTo(403);
    }

    // =========================================================================
    // Contracts — CONTRACT_MANAGER role
    // =========================================================================

    @Test
    void contractManager_canCreateContract() {
        // Create an application to reference (RBAC check — not business logic)
        Response appResp = api.createApplication(adminToken, adminUserId, true, "draft");
        Long appId;
        if (appResp.statusCode() == 409) {
            appId = api.listApplications(adminToken).jsonPath().getLong("content[0].id");
        } else {
            appId = appResp.jsonPath().getLong("id");
        }

        Response response = api.createContract(contractManagerToken, appId, "budget");
        assertThat(response.statusCode())
                .as("Contract Manager should be authorized to create contracts (not 403)")
                .isNotEqualTo(403);
    }

    @Test
    void operator_cannotCreateContract() {
        Response response = api.createContract(operatorToken, 999999L, "budget");
        assertThat(response.statusCode())
                .as("Operator should NOT be able to create contracts")
                .isEqualTo(403);
    }

    // =========================================================================
    // Orders — EXECUTIVE_SECRETARY role
    // =========================================================================

    @Test
    void executiveSecretary_canCreateOrder() {
        // Create an application to reference
        Response appResp = api.createApplication(adminToken, adminUserId, true, "draft");
        Long appId;
        if (appResp.statusCode() == 409) {
            appId = api.listApplications(adminToken).jsonPath().getLong("content[0].id");
        } else {
            appId = appResp.jsonPath().getLong("id");
        }

        Response response = api.createOrder(
                executiveSecretaryToken, executiveSecretaryUserId,
                "enrollment", java.util.List.of(appId));
        // May be 201 or a validation error (app not in correct state), but NOT 403
        assertThat(response.statusCode())
                .as("Executive Secretary should be authorized to create orders (not 403)")
                .isNotEqualTo(403);
    }

    @Test
    void contractManager_cannotCreateOrder() {
        Response response = api.createOrder(
                contractManagerToken, contractManagerUserId,
                "enrollment", java.util.List.of(999999L));
        assertThat(response.statusCode())
                .as("Contract Manager should NOT be able to create orders")
                .isEqualTo(403);
    }

    // =========================================================================
    // Admin users — CONTRACT_MANAGER and EXECUTIVE_SECRETARY cannot access
    // =========================================================================

    @Test
    void contractManager_cannotAccessAdminUsers() {
        Response response = api.listUsers(contractManagerToken);
        assertThat(response.statusCode())
                .as("Contract Manager should NOT access admin users list")
                .isEqualTo(403);
    }

    @Test
    void executiveSecretary_cannotAccessAdminUsers() {
        Response response = api.listUsers(executiveSecretaryToken);
        assertThat(response.statusCode())
                .as("Executive Secretary should NOT access admin users list")
                .isEqualTo(403);
    }
}
