version = "0.1.0"

application {
    mainClassName = "dk.sdu.cloud.mail.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation("com.sun.mail:javax.mail:1.5.5")
}
