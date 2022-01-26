version = "2022.1.0-patch.4"

application {
    mainClassName = "dk.sdu.cloud.file.ucloud.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":notification-service:api"))
            implementation(project(":accounting-service:api"))
        }
    }
}
