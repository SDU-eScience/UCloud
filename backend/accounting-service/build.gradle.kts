version = "1.2.0-projects.2"

application {
    mainClassName = "dk.sdu.cloud.accounting.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
}
