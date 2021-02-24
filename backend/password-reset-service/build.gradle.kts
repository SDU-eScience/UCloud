version = "0.3.0-rc0"

application {
    mainClassName = "dk.sdu.cloud.password.reset.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":mail-service:api"))
}
