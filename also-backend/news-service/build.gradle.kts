version = "0.2.0-rc0"

application {
    mainClassName = "dk.sdu.cloud.news.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
}
