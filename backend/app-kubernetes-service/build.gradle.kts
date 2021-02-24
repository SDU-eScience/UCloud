version = "0.20.0-rc15"

application {
    mainClassName = "dk.sdu.cloud.app.kubernetes.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":app-orchestrator-service:api"))
}
