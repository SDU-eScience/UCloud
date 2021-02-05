version = "1.5.0-rc1"

application {
    mainClassName = "dk.sdu.cloud.accounting.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":project-service:api"))
    implementation(project(":mail-service:api"))
}
