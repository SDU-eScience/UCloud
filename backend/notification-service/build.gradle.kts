version = "1.4.0-rc0"

application {
    mainClassName = "dk.sdu.cloud.notification.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
        }
    }
}
