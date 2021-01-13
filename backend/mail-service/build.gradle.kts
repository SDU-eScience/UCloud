version = "0.3.0-rc0"

application {
    mainClassName = "dk.sdu.cloud.mail.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation("com.sun.mail:javax.mail:1.5.5")
    implementation(project(":slack-service:api"))
}
