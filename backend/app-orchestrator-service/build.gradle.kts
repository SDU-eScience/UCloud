version = "2.4.0-rc8"

application {
    mainClassName = "dk.sdu.cloud.app.orchestrator.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":app-store-service:api"))
    implementation(project(":accounting-service:api"))
    implementation(project(":storage-service:api"))
    implementation(project(":project-service:api"))
    implementation(project(":mail-service:api"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.4")
}
