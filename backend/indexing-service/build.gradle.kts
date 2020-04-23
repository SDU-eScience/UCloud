version = "1.16.3"

application {
    mainClassName = "dk.sdu.cloud.indexing.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    api(project(":storage-service:api"))
    implementation("net.java.dev.jna:jna:5.2.0")
    implementation("mbuhot:eskotlin:0.4.0")
}
