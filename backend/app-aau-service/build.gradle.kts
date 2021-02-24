version = "0.1.0"

application {
    mainClassName = "dk.sdu.cloud.app.aau.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":slack-service:api"))
}