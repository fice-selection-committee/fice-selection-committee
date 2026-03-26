val versionFromFile = file("version.properties")
    .readLines()
    .first { it.startsWith("version=") }
    .substringAfter("=")
    .trim()

val isRelease = System.getenv("GITHUB_REF_TYPE") == "tag"
val effectiveVersion = if (isRelease) versionFromFile else "$versionFromFile-SNAPSHOT"

allprojects {
    group = "edu.kpi.fice"
    version = effectiveVersion

    repositories {
        mavenCentral()
    }
}
