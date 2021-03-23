version = "2.4.2"

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
