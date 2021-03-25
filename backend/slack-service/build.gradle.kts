version = "0.3.2"

application {
    mainClassName = "dk.sdu.cloud.slack.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
        }
    }
}
