version = "0.1.0"

application {
    mainClassName = "dk.sdu.cloud.file.synchronization.MainKt"
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
