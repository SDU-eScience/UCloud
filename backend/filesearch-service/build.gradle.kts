version = "1.3.0"

application {
    mainClassName = "dk.sdu.cloud.filesearch.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    api(project(":storage-service:api"))
    implementation(project(":indexing-service:api"))
    implementation(project(":share-service:api"))
    implementation(project(":project-service:api"))
}
