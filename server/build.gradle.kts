val versionFromFile = file("version.properties")
    .readLines()
    .first { it.startsWith("version=") }
    .substringAfter("=")
    .trim()

val effectiveVersion = versionFromFile

allprojects {
    group = "edu.kpi.fice"
    version = effectiveVersion

    repositories {
        mavenCentral()
    }
}
