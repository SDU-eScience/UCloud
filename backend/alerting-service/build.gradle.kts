version = "1.2.4"

application {
    mainClassName = "dk.sdu.cloud.alerting.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation("io.fabric8:kubernetes-client:4.1.3")
    implementation(project(":slack-service:api"))
}
