plugins {
    java
    jacoco
}

jacoco {
    toolVersion = "0.8.11"
    reportsDirectory.set(layout.buildDirectory.dir("reports/coverage"))
}

tasks.named<Test>("test") {
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named<Test>("test"))

    group = "Reporting"
    description = "Generates code coverage report"

    executionData.setFrom(fileTree(layout.buildDirectory).include("jacoco/*.exec"))

    reports {
        csv.required.set(true)
        xml.required.set(true)
        html.required.set(true)

        xml.outputLocation.set(layout.buildDirectory.file("reports/coverage/coverage.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/coverage"))
        csv.outputLocation.set(layout.buildDirectory.file("reports/coverage/coverage.csv"))
    }

    finalizedBy(tasks.named("jacocoTestCoverageVerification"))
}

afterEvaluate {
    // Ensure JaCoCo tasks run after integrationTest when both are in the task graph,
    // so integrationTest.exec is fully written before JaCoCo reads jacoco/*.exec.
    tasks.findByName("integrationTest")?.let { intTest ->
        tasks.named<JacocoReport>("jacocoTestReport") {
            mustRunAfter(intTest)
        }
        tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
            mustRunAfter(intTest)
        }
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        val excludePatterns = project.extensions.extraProperties.let {
            if (it.has("filesExcludedFromCoverage")) {
                @Suppress("UNCHECKED_CAST")
                it.get("filesExcludedFromCoverage") as List<String>
            } else {
                emptyList()
            }
        }
        classDirectories.setFrom(files(classDirectories.files.map {
            fileTree(it) { exclude(excludePatterns) }
        }))
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    executionData.setFrom(fileTree(layout.buildDirectory).include("jacoco/*.exec"))

    doFirst {
        val minCoverage = project.extensions.extraProperties.let {
            if (it.has("minimumCoveragePerFile")) {
                it.get("minimumCoveragePerFile").toString().toBigDecimal()
            } else {
                "0.8".toBigDecimal()
            }
        }
        val excludePatterns = project.extensions.extraProperties.let {
            if (it.has("filesExcludedFromCoverage")) {
                @Suppress("UNCHECKED_CAST")
                it.get("filesExcludedFromCoverage") as List<String>
            } else {
                emptyList()
            }
        }

        violationRules {
            rule {
                isEnabled = true
                element = "SOURCEFILE"
                limit {
                    counter = "LINE"
                    minimum = minCoverage
                }
                excludes = excludePatterns
            }
        }
    }
}
