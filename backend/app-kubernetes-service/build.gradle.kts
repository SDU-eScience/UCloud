version = "0.19.9"

application {
    mainClassName = "dk.sdu.cloud.app.kubernetes.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":app-orchestrator-service:api"))
}
