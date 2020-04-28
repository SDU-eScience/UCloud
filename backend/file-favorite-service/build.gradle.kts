version = "1.5.3"

application {
    mainClassName = "dk.sdu.cloud.file.favorite.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":storage-service:api"))
}
