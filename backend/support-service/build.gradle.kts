version = "1.4.2"

application {
    mainClassName = "dk.sdu.cloud.support.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":slack-service:api"))
}
