version = "0.1.6-23"

application {
    mainClassName = "dk.sdu.cloud.ucloud.data.extraction.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":accounting-service:api"))
}
