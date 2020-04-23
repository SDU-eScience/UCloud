version = "0.1.0-PASSWORD-RESET-TEST-19"

application {
    mainClassName = "dk.sdu.cloud.password.reset.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":mail-service:api"))
}
