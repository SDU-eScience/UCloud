version = "1.6.0-rc1"

application {
    mainClassName = "dk.sdu.cloud.file.trash.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":storage-service:api"))
            implementation(project(":task-service:api"))
        }
    }
}
