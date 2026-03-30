plugins {
    id("sc.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// Exclude default logging in favour of Log4j2
configurations.configureEach {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}

val catalog = the<VersionCatalogsExtension>().named("libs")

dependencyManagement {
    imports {
        mavenBom(catalog.findLibrary("spring-cloud-bom").get().get().toString())
    }
}

dependencies {
    // ── Common Spring Boot starters (versions from Spring Boot BOM) ──
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(catalog.findLibrary("dotenv").get())

    // ── Observability (versions from Spring Boot / Micrometer BOM) ──
    implementation(catalog.findLibrary("micrometer-prometheus").get())
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")

    // ── OpenAPI ───────────────────────────────────────────
    implementation(catalog.findLibrary("springdoc-openapi-webmvc").get())
}
