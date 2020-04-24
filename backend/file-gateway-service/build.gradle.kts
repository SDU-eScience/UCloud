version = "1.5.0-projects.0"

application {
    mainClassName = "dk.sdu.cloud.file.gateway.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":storage-service:api"))
    implementation(project(":file-favorite-service:api"))
    implementation(project(":filesearch-service:api"))
    implementation(project(":share-service:api"))
}
