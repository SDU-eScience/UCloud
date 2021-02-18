version = "0.2.8-1"

application {
    mainClassName = "dk.sdu.cloud.mail.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation("com.sun.mail:javax.mail:1.5.5")
    implementation(project(":slack-service:api"))
}
