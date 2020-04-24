version = "1.4.0-projects.1"

application {
    mainClassName = "dk.sdu.cloud.accounting.storage.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":storage-service:api"))
    implementation(project(":indexing-service:api"))
    implementation(project(":accounting-service:api"))
    implementation(project(":project-service:api"))
}
