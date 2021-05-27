version = "1.7.2"

application {
    mainClassName = "dk.sdu.cloud.activity.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":app-orchestrator-service:api"))
            implementation(project(":app-store-service:api"))
        }
    }
}
