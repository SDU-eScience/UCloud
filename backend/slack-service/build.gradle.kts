version = "0.1.2"

application {
    mainClassName = "dk.sdu.cloud.slack.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
}
