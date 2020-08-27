version = "0.2.1"

application {
    mainClassName = "dk.sdu.cloud.password.reset.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":mail-service:api"))
}
