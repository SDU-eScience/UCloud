version = "4.4.2"

application {
    mainClassName = "dk.sdu.cloud.file.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":task-service:api"))
            implementation(project(":notification-service:api"))
            implementation(project(":project-service:api"))
            implementation(project(":accounting-service:api"))
            implementation("net.java.dev.jna:jna:5.2.0")
            implementation("org.kamranzafar:jtar:2.3")
            implementation("org.apache.commons:commons-compress:1.20")
        }
    }
}
