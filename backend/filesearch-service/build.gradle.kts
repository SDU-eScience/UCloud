version = "1.4.0-rc1"

application {
    mainClassName = "dk.sdu.cloud.filesearch.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":storage-service:api"))
    implementation(project(":indexing-service:api"))
    implementation(project(":share-service:api"))
    implementation(project(":project-service:api"))
}
