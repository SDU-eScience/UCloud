version = "2022.1.4"

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