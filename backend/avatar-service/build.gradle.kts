version = "1.5.0"

application {
    mainClassName = "dk.sdu.cloud.avatar.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
        }
    }
}
