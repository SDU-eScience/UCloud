version = "2022.1.3-patch.2"

application {
    mainClassName = "dk.sdu.cloud.app.kubernetes.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":app-orchestrator-service:api"))
            implementation(project(":file-orchestrator-service:api"))
            implementation(project(":file-ucloud-service:util"))
        }
    }
}
