version = "0.2.0"

application {
    mainClassName = "dk.sdu.cloud.app.kubernetes.watcher.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation("io.fabric8:kubernetes-client:4.6.4")
}
