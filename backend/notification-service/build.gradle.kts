version = "1.5.3"

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
