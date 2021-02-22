version = "0.1.2"

application {
    mainClassName = "dk.sdu.cloud.app.aau.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":slack-service:api"))
}
