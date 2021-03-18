version = "1.5.2"

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
