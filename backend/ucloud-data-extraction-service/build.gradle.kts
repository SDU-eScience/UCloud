version = "0.1.7-6"

application {
    mainClassName = "dk.sdu.cloud.ucloud.data.extraction.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":project-service:api"))
}
