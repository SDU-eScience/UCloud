version = "0.1.18"

application {
    mainClassName = "dk.sdu.cloud.ucloud.data.extraction.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":project-service:api"))
}