version = "0.2.2"

application {
    mainClassName = "dk.sdu.cloud.audit.ingestion.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
}
