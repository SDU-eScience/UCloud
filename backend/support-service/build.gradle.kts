version = "1.5.0"

application {
    mainClassName = "dk.sdu.cloud.support.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":slack-service:api"))
    implementation(project(":mail-service:api"))

}
