package edu.kpi.fice.e2e.fixture;

/**
 * Constants for test users used across E2E tests.
 * <p>
 * Passwords satisfy the identity-service validation: at least 8 characters,
 * one uppercase, one lowercase, one digit.
 */
public final class TestUsers {

    private TestUsers() {
    }

    // --- Admin ---
    public static final String ADMIN_EMAIL = "admin@e2e-test.kpi.ua";
    public static final String ADMIN_PASSWORD = "Admin1Pass123";
    public static final String ADMIN_FIRST_NAME = "Admin";
    public static final String ADMIN_LAST_NAME = "E2ETest";

    // --- Applicant ---
    public static final String APPLICANT_EMAIL = "applicant@e2e-test.kpi.ua";
    public static final String APPLICANT_PASSWORD = "Applicant1Pass123";
    public static final String APPLICANT_FIRST_NAME = "Applicant";
    public static final String APPLICANT_LAST_NAME = "E2ETest";

    // --- Operator ---
    public static final String OPERATOR_EMAIL = "operator@e2e-test.kpi.ua";
    public static final String OPERATOR_PASSWORD = "Operator1Pass123";
    public static final String OPERATOR_FIRST_NAME = "Operator";
    public static final String OPERATOR_LAST_NAME = "E2ETest";

    // --- Contract Manager ---
    public static final String CONTRACT_MANAGER_EMAIL = "contract-manager@e2e-test.kpi.ua";
    public static final String CONTRACT_MANAGER_PASSWORD = "ContractMgr1Pass123";
    public static final String CONTRACT_MANAGER_FIRST_NAME = "ContractMgr";
    public static final String CONTRACT_MANAGER_LAST_NAME = "E2ETest";

    // --- Executive Secretary ---
    public static final String EXECUTIVE_SECRETARY_EMAIL = "exec-secretary@e2e-test.kpi.ua";
    public static final String EXECUTIVE_SECRETARY_PASSWORD = "ExecSecretary1Pass123";
    public static final String EXECUTIVE_SECRETARY_FIRST_NAME = "ExecSecretary";
    public static final String EXECUTIVE_SECRETARY_LAST_NAME = "E2ETest";
}
