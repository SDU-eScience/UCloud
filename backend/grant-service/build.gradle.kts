version = "0.1.16"

application {
    mainClassName = "dk.sdu.cloud.grant.MainKt"
}

dependencies {
    implementation(project(":project-service:api"))
    implementation(project(":accounting-service:api"))
    implementation(project(":auth-service:api"))
    implementation(project(":mail-service:api"))
    implementation(project(":notification-service:api"))
    implementation(project(":storage-service:api"))
}
