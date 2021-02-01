version = "1.1.6"

application {
    mainClassName = "dk.sdu.cloud.elastic.management.MainKt"
}

dependencies {
    implementation("mbuhot:eskotlin:0.7.0")
    implementation(project(":slack-service:api"))
    implementation(project(":auth-service:api"))
}
