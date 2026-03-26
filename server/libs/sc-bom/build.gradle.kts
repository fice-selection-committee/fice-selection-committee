plugins {
    `java-platform`
    `maven-publish`
}

group = rootProject.group
version = rootProject.version

dependencies {
    constraints {
        api(project(":libs:sc-common"))
        api(project(":libs:sc-auth-starter"))
        api(project(":libs:sc-event-contracts"))
        api(project(":libs:sc-test-common"))
        api(project(":libs:sc-observability-starter"))
        api(project(":libs:sc-s3-starter"))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenBom") {
            from(components["javaPlatform"])

            pom {
                name.set("FICE Selection Committee BOM")
                description.set("Bill of Materials for FICE SC shared libraries")
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
