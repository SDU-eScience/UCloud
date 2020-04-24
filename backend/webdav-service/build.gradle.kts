version = "0.1.13"

application {
    mainClassName = "dk.sdu.cloud.webdav.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":storage-service:api"))
    implementation(project(":project-service:api"))
    implementation(project(":project-repository-service:api"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.10.0.pr3")
}
