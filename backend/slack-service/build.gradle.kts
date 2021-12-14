version = "2021.3.0-alpha1"

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
