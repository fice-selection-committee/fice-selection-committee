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
    // ── Common Spring Boot starters ───────────────────────
    implementation(catalog.findLibrary("spring-boot-starter-web").get())
    implementation(catalog.findLibrary("spring-boot-starter-validation").get())
    implementation(catalog.findLibrary("spring-boot-starter-log4j2").get())
    implementation(catalog.findLibrary("spring-boot-starter-actuator").get())
    implementation(catalog.findLibrary("dotenv").get())

    // ── Observability ─────────────────────────────────────
    implementation(catalog.findLibrary("micrometer-prometheus").get())
    implementation(catalog.findLibrary("micrometer-tracing-brave").get())
    implementation(catalog.findLibrary("zipkin-reporter-brave").get())

    // ── OpenAPI ───────────────────────────────────────────
    implementation(catalog.findLibrary("springdoc-openapi-webmvc").get())
}
