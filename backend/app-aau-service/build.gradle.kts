version = "0.2.6"

application {
    mainClassName = "dk.sdu.cloud.app.aau.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":slack-service:api"))
            implementation(project(":provider-service:api"))
            implementation(project(":project-service:api"))
        }
    }
}
