version = "1.3.0-projects.4"

application {
    mainClassName = "dk.sdu.cloud.accounting.compute.MainKt"
}

dependencies {
    api(project(":accounting-service:api"))
    implementation(project(":app-orchestrator-service:api"))
    implementation(project(":auth-service:api"))
}
