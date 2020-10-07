version = "1.6.2"

application {
    mainClassName = "dk.sdu.cloud.file.favorite.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":storage-service:api"))
}
