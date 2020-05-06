version = "0.1.7"

application {
    mainClassName = "dk.sdu.cloud.app.kubernetes.watcher.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation("io.fabric8:kubernetes-client:4.6.4")
}
