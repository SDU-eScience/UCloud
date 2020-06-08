version = "1.3.0"

application {
    mainClassName = "dk.sdu.cloud.accounting.compute.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":project-service:api"))
}
