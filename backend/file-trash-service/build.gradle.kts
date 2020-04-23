version = "1.4.5"

application {
    mainClassName = "dk.sdu.cloud.file.trash.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":storage-service:api"))
    implementation(project(":task-service:api"))
}
