version = "0.1.0"

application {
    mainClassName = "dk.sdu.cloud.provider.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
}