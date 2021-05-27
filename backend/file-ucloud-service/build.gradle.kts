version = "0.1.0"

application {
    mainClassName = "dk.sdu.cloud.file.ucloud.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":task-service:api"))
            implementation(project(":notification-service:api"))
            implementation(project(":accounting-service:api"))
            implementation("net.java.dev.jna:jna:5.2.0")
        }
    }
}