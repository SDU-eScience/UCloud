version = "0.20.1"

application {
    mainClassName = "dk.sdu.cloud.app.kubernetes.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":app-orchestrator-service:api"))
}
