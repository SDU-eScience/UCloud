version = "0.1.1"

application {
    mainClassName = "dk.sdu.cloud.slack.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
}
