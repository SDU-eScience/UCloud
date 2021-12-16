version = "1.6.7"

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