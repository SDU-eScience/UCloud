version = "2.2.0"

application {
    mainClassName = "dk.sdu.cloud.file.stats.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":indexing-service:api"))
    implementation(project(":storage-service:api"))
}
