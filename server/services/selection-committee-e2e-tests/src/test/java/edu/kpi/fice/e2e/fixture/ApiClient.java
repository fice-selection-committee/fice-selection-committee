package edu.kpi.fice.e2e.fixture;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Thin wrapper around REST Assured that targets individual service URLs
 * (bypassing the gateway) for reliability.
 * <p>
 * Every method returns the raw {@link Response} so callers can assert
 * status codes and extract bodies as needed.
 */
public final class ApiClient {

    private final String identityUrl;
    private final String admissionUrl;
    private final String documentsUrl;
    private final String environmentUrl;

    public ApiClient(String identityUrl, String admissionUrl,
                     String documentsUrl, String environmentUrl) {
        this.identityUrl = identityUrl;
        this.admissionUrl = admissionUrl;
        this.documentsUrl = documentsUrl;
        this.environmentUrl = environmentUrl;
    }

    // ------------------------------------------------------------------ Auth

    /**
     * POST /api/v1/auth/register
     */
    public Response register(String firstName, String lastName, String email, String password) {
        return given()
                .baseUri(identityUrl)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "firstName", firstName,
                        "lastName", lastName,
                        "email", email,
                        "password", password
                ))
                .when()
                .post("/api/v1/auth/register");
    }

    /**
     * POST /api/v1/auth/login — returns the full response; extract accessToken from body.
     */
    public Response login(String email, String password) {
        return given()
                .baseUri(identityUrl)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "email", email,
                        "password", password
                ))
                .when()
                .post("/api/v1/auth/login");
    }

    /**
     * POST /api/v1/auth/user — returns current authenticated user info.
     */
    public Response getCurrentUser(String accessToken) {
        return given()
                .baseUri(identityUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .post("/api/v1/auth/user");
    }

    /**
     * POST /api/v1/auth/verify — verify email with token.
     */
    public Response verifyEmail(String token) {
        return given()
                .baseUri(identityUrl)
                .contentType(ContentType.JSON)
                .body(Map.of("token", token))
                .when()
                .post("/api/v1/auth/verify");
    }

    // --------------------------------------------------------- Admin / Users

    /**
     * GET /api/v1/admin/users — list all users (ADMIN only).
     */
    public Response listUsers(String accessToken) {
        return given()
                .baseUri(identityUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/admin/users");
    }

    /**
     * PUT /api/v1/admin/users/{id}/role — change a user's role.
     */
    public Response changeUserRole(String accessToken, Long userId, String roleName) {
        return given()
                .baseUri(identityUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of("roleName", roleName))
                .when()
                .put("/api/v1/admin/users/" + userId + "/role");
    }

    /**
     * GET /api/v1/admin/users/by-role?roleName=...
     */
    public Response getUsersByRole(String accessToken, String roleName) {
        return given()
                .baseUri(identityUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("roleName", roleName)
                .when()
                .get("/api/v1/admin/users/by-role");
    }

    // ----------------------------------------------------------- Faculties

    /**
     * POST /api/v1/faculties
     */
    public Response createFaculty(String accessToken, String name) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of("name", name))
                .when()
                .post("/api/v1/faculties");
    }

    /**
     * GET /api/v1/faculties
     */
    public Response listFaculties(String accessToken) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/faculties");
    }

    // ----------------------------------------------------------- Cathedras

    /**
     * GET /api/v1/cathedras?facultyId=...
     */
    public Response listCathedras(String accessToken, Long facultyId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("facultyId", facultyId)
                .when()
                .get("/api/v1/cathedras");
    }

    /**
     * POST /api/v1/cathedras
     */
    public Response createCathedra(String accessToken, String name, Long facultyId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of("name", name, "facultyId", facultyId))
                .when()
                .post("/api/v1/cathedras");
    }

    // ------------------------------------------------ Educational Programs

    /**
     * GET /api/v1/educational-programs?cathedraId=...
     */
    public Response listEducationalPrograms(String accessToken, Long cathedraId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("cathedraId", cathedraId)
                .when()
                .get("/api/v1/educational-programs");
    }

    /**
     * POST /api/v1/educational-programs
     */
    public Response createEducationalProgram(String accessToken, String name, Long cathedraId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of("name", name, "cathedraId", cathedraId))
                .when()
                .post("/api/v1/educational-programs");
    }

    // --------------------------------------------------------- Applications

    /**
     * POST /api/v1/admissions — create an application.
     */
    public Response createApplication(String accessToken, Long applicantUserId, boolean sex, String status) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of(
                        "applicantUserId", applicantUserId,
                        "sex", sex,
                        "status", status
                ))
                .when()
                .post("/api/v1/admissions");
    }

    /**
     * GET /api/v1/admissions — list applications (paginated).
     */
    public Response listApplications(String accessToken) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/admissions");
    }

    /**
     * GET /api/v1/admissions/{id}
     */
    public Response getApplication(String accessToken, Long applicationId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/admissions/" + applicationId);
    }

    /**
     * PUT /api/v1/admissions/{id}/submit
     */
    public Response submitApplication(String accessToken, Long applicationId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .put("/api/v1/admissions/" + applicationId + "/submit");
    }

    /**
     * PUT /api/v1/admissions/{id}/review?operator={operatorId}
     */
    public Response reviewApplication(String accessToken, Long applicationId, Long operatorId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("operator", operatorId)
                .when()
                .put("/api/v1/admissions/" + applicationId + "/review");
    }

    /**
     * PUT /api/v1/admissions/{id}/accept
     */
    public Response acceptApplication(String accessToken, Long applicationId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .put("/api/v1/admissions/" + applicationId + "/accept");
    }

    /**
     * PUT /api/v1/admissions/{id}/reject
     */
    public Response rejectApplication(String accessToken, Long applicationId, String reason) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of("rejectionReason", reason))
                .when()
                .put("/api/v1/admissions/" + applicationId + "/reject");
    }

    /**
     * PUT /api/v1/admissions/{id}/enroll
     */
    public Response enrollApplication(String accessToken, Long applicationId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .put("/api/v1/admissions/" + applicationId + "/enroll");
    }

    /**
     * DELETE /api/v1/admissions/{id}
     */
    public Response deleteApplication(String accessToken, Long applicationId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .delete("/api/v1/admissions/" + applicationId);
    }

    // ------------------------------------------------------------ Documents

    /**
     * POST /api/v1/documents — create document metadata.
     */
    public Response createDocument(String accessToken, int year, String fileName,
                                   String documentType, String contentType, long sizeBytes) {
        return given()
                .baseUri(documentsUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of(
                        "year", year,
                        "temp", false,
                        "fileName", fileName,
                        "type", documentType,
                        "contentType", contentType,
                        "sizeBytes", sizeBytes
                ))
                .when()
                .post("/api/v1/documents");
    }

    /**
     * POST /api/v1/documents/{id}/presign — get a presigned upload URL.
     */
    public Response presignUpload(String accessToken, Long documentId) {
        return given()
                .baseUri(documentsUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .post("/api/v1/documents/" + documentId + "/presign");
    }

    /**
     * GET /api/v1/documents?ownerUserId=...
     */
    public Response getDocumentsByOwner(String accessToken, Long ownerUserId) {
        return given()
                .baseUri(documentsUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("ownerUserId", ownerUserId)
                .when()
                .get("/api/v1/documents");
    }

    /**
     * GET /api/v1/documents/{id}
     */
    public Response getDocument(String accessToken, Long documentId) {
        return given()
                .baseUri(documentsUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/documents/" + documentId);
    }

    // ------------------------------------------------------------ Contracts

    /**
     * POST /api/v1/contracts — create a contract draft.
     */
    public Response createContract(String accessToken, Long applicationId, String contractType) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of(
                        "applicationId", applicationId,
                        "contractType", contractType
                ))
                .when()
                .post("/api/v1/contracts");
    }

    /**
     * PUT /api/v1/contracts/{id}/register — register a contract.
     */
    public Response registerContract(String accessToken, Long contractId, String contractNumber) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of("contractNumber", contractNumber))
                .when()
                .put("/api/v1/contracts/" + contractId + "/register");
    }

    /**
     * GET /api/v1/contracts?applicationId=...
     */
    public Response getContractsByApplication(String accessToken, Long applicationId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("applicationId", applicationId)
                .when()
                .get("/api/v1/contracts");
    }

    // --------------------------------------------------------------- Groups

    /**
     * GET /api/v1/groups?educationalProgramId=...&enrollmentYear=...
     */
    public Response listGroups(String accessToken, Long educationalProgramId, Integer enrollmentYear) {
        var req = given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken);
        if (educationalProgramId != null) {
            req = req.queryParam("educationalProgramId", educationalProgramId);
        }
        if (enrollmentYear != null) {
            req = req.queryParam("enrollmentYear", enrollmentYear);
        }
        return req.when().get("/api/v1/groups");
    }

    /**
     * POST /api/v1/groups
     */
    public Response createGroup(String accessToken, Integer enrollmentYear, String code,
                                Long educationalProgramId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of(
                        "enrollmentYear", enrollmentYear,
                        "code", code,
                        "educationalProgramId", educationalProgramId
                ))
                .when()
                .post("/api/v1/groups");
    }

    // ------------------------------------------------------- Group Assignments

    /**
     * POST /api/v1/group-assignments — assign application to group.
     */
    public Response assignToGroup(String accessToken, Long applicationId, Long groupId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of(
                        "applicationId", applicationId,
                        "groupId", groupId
                ))
                .when()
                .post("/api/v1/group-assignments");
    }

    // --------------------------------------------------------------- Orders

    /**
     * POST /api/v1/orders — create an enrollment/expulsion order.
     * Requires X-User-Id header.
     */
    public Response createOrder(String accessToken, Long userId, String orderType,
                                List<Long> applicationIds) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .header("X-User-Id", userId)
                .body(Map.of(
                        "orderType", orderType,
                        "applicationIds", applicationIds
                ))
                .when()
                .post("/api/v1/orders");
    }

    /**
     * PUT /api/v1/orders/{id}/sign — requires X-User-Id header.
     */
    public Response signOrder(String accessToken, Long userId, Long orderId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .header("X-User-Id", userId)
                .when()
                .put("/api/v1/orders/" + orderId + "/sign");
    }

    /**
     * GET /api/v1/orders/{id}
     */
    public Response getOrder(String accessToken, Long orderId) {
        return given()
                .baseUri(admissionUrl)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/orders/" + orderId);
    }
}
