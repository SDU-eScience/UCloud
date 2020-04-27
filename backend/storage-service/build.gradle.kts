version = "4.1.0-projects.14"

application {
    mainClassName = "dk.sdu.cloud.file.MainKt"
}



dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":task-service:api"))
    implementation(project(":notification-service:api"))
    implementation(project(":project-service:api"))
    implementation("net.java.dev.jna:jna:5.2.0")
    implementation(group = "com.h2database", name = "h2", version = "1.4.196")
    implementation("org.kamranzafar:jtar:2.3")
}
