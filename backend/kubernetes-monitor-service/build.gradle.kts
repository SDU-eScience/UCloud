version = "0.2.0"

application {
    mainClassName = "dk.sdu.cloud.kubernetes.monitor.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation("io.fabric8:kubernetes-client:4.6.4")
}
