plugins {
    java
    id("io.qameta.allure")
}

allure {
    version.set("2.30.0")
    adapter {
        autoconfigure.set(true)
        aspectjWeaver.set(false)
    }
}

val catalog = the<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation(catalog.findLibrary("spring-boot-starter-test").get())
    testRuntimeOnly(catalog.findLibrary("junit-platform-launcher").get())
    testImplementation(catalog.findLibrary("rest-assured").get())
}

// ── Integration test source set ───────────────────────────
sourceSets {
    create("integrationTest") {
        java {
            compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
            runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
            srcDir(file("src/integrationTest/java"))
        }
        resources.srcDir(file("src/integrationTest/resources"))
    }
}

configurations {
    named("integrationTestImplementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    named("integrationTestRuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
}

tasks.named<ProcessResources>("processIntegrationTestResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests with Testcontainers."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform {
        if (project.hasProperty("includeTags")) {
            includeTags(*project.property("includeTags").toString().split(",").toTypedArray())
        }
        if (project.hasProperty("excludeTags")) {
            excludeTags(*project.property("excludeTags").toString().split(",").toTypedArray())
        }
    }
    shouldRunAfter(tasks.named("test"))
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        if (project.hasProperty("includeTags")) {
            includeTags(*project.property("includeTags").toString().split(",").toTypedArray())
        }
        if (project.hasProperty("excludeTags")) {
            excludeTags(*project.property("excludeTags").toString().split(",").toTypedArray())
        }
    }
}
