plugins {
    `java-library`
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
