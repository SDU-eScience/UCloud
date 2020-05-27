version = "0.1.3"

application {
    mainClassName = "dk.sdu.cloud.kubernetes.monitor.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation("io.fabric8:kubernetes-client:4.6.4")
}
