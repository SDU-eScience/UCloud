version = "0.2.1"

application {
    mainClassName = "dk.sdu.cloud.app.aau.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":slack-service:api"))
        }
    }
}
