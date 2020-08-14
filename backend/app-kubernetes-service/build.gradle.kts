version = "0.19.0"

application {
    mainClassName = "dk.sdu.cloud.app.kubernetes.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":app-orchestrator-service:api"))
    implementation(project(":app-kubernetes-watcher-service:api"))
    implementation("io.fabric8:kubernetes-client:4.6.4")
}
