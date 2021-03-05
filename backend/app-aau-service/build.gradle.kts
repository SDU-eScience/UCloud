version = "0.1.2"

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