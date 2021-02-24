version = "1.7.0-rc1"

application {
    mainClassName = "dk.sdu.cloud.file.favorite.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":storage-service:api"))
}
