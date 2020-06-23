version = "0.1.0"

application {
    mainClassName = "dk.sdu.cloud.news.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
}