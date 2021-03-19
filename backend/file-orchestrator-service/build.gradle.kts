version = "0.1.0"

application {
    mainClassName = "dk.sdu.cloud.file.orchestrator.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
        }
    }
}
