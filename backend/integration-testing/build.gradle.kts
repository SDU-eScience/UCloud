version = "0.2.4"

application {
    mainClassName = "dk.sdu.cloud.integration.MainKt"
}

dependencies {
    implementation(project(":service-common"))
    implementation(project(":auth-service:api"))
    implementation(project(":avatar-service:api"))
    implementation(project(":storage-service:api"))
    implementation(project(":app-store-service:api"))
    implementation(project(":app-orchestrator-service:api"))
    implementation(project(":share-service:api"))
    implementation(project(":file-favorite-service:api"))
    implementation(project(":accounting-service:api"))
    implementation(project(":accounting-compute-service:api"))
    implementation(project(":accounting-storage-service:api"))
    implementation(project(":filesearch-service:api"))
    implementation(project(":activity-service:api"))
    implementation(project(":notification-service:api"))
    implementation(project(":file-trash-service:api"))
    implementation(project(":file-gateway-service:api"))
    implementation(project(":support-service:api"))
}
