version = "1.8.0"

application {
    mainClassName = "dk.sdu.cloud.share.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":storage-service:api"))
    implementation(project(":notification-service:api"))
    implementation(project(":contact-book-service:api"))
}
