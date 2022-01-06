rootProject.name = "ucloud"

include("service-lib")
include("service-lib-test")
include("launcher")

// Automatically pull in sub-projects
(rootProject.projectDir.listFiles() ?: emptyArray()).forEach { file ->
    val buildGradle = File(file, "build.gradle.kts")
    if (file.isDirectory && buildGradle.exists()) {
        include(file.name)
        val apiPackage = File(file, "api")
        val apiBuild = File(apiPackage, "build.gradle.kts")
        if (apiBuild.exists()) {
            include("${file.name}:api")
        }

        val utilPackage = File(file, "util")
        val utilBuild = File(utilPackage, "build.gradle.kts")
        if (utilBuild.exists()) {
            include("${file.name}:util")
        }
    }
}
