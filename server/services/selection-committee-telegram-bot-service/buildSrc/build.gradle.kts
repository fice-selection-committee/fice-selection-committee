plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.6")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:3.1.0")
    implementation("io.qameta.allure.gradle.allure:allure-plugin:2.12.0")
}
