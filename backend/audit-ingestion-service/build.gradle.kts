version = "0.2.1"

application {
    mainClassName = "dk.sdu.cloud.audit.ingestion.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
}
