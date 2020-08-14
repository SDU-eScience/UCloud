version = "0.2.0"

application {
    mainClassName = "dk.sdu.cloud.project.repository.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":storage-service:api"))
    implementation(project(":project-service:api"))
}
