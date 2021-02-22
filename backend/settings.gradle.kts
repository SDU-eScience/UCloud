rootProject.name = "ucloud"

include("service-lib")
/*
include("service-common-test")
include("integration-testing")
include("launcher")
*/

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
    }
}
