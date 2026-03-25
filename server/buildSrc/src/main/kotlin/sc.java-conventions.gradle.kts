import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

val catalog = the<VersionCatalogsExtension>().named("libs")

dependencies {
    compileOnly(catalog.findLibrary("lombok").get())
    annotationProcessor(catalog.findLibrary("lombok").get())
    testCompileOnly(catalog.findLibrary("lombok").get())
    testAnnotationProcessor(catalog.findLibrary("lombok").get())

    "errorprone"(catalog.findLibrary("errorprone-core").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        isEnabled.set(true)
        errorproneArgs.addAll(
            "-Xep:UnusedVariable:WARN",
            "-Xep:EqualsHashCode:ERROR"
        )
    }
}

spotless {
    java {
        googleJavaFormat("1.17.0")
        target("src/**/*.java")
    }
}
