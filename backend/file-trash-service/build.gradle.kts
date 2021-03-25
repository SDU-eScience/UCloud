version = "1.7.2"

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
