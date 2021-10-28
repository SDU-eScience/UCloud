version = "2021.3.0-alpha0"

application {
    mainClassName = "dk.sdu.cloud.app.aau.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":slack-service:api"))
            implementation(project(":accounting-service:api"))
        }
    }
}
