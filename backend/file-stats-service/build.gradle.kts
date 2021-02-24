version = "2.3.0-rc1"

application {
    mainClassName = "dk.sdu.cloud.file.stats.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":indexing-service:api"))
            implementation(project(":storage-service:api"))
        }
    }
}
