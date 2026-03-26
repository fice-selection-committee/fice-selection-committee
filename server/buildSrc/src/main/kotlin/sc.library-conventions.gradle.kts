plugins {
    `java-library`
    `maven-publish`
    id("sc.java-conventions")
    id("io.spring.dependency-management")
}

val catalog = the<VersionCatalogsExtension>().named("libs")

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${catalog.findVersion("spring-boot").get()}")
    }
}

// Libraries should not produce a Spring Boot fat jar
tasks.whenTaskAdded {
    if (name == "bootJar") {
        enabled = false
    }
}

tasks.named<Jar>("jar") {
    enabled = true
}

// ── Publishing ──────────────────────────────────────────────

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Javadoc>().configureEach {
    options {
        (this as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            // Write resolved versions into the POM so consumers get explicit version numbers
            // (required because io.spring.dependency-management manages versions via BOM import)
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }

            pom {
                name.set(project.name)
                description.set("FICE Selection Committee shared library: ${project.name}")
                url.set("https://github.com/fice-selection-committee/fice-selection-committee")
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/fice-selection-committee/fice-selection-committee")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String? ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as String? ?: ""
            }
        }
    }
}
