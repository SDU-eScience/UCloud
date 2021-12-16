version = "0.1.8"

application {
    mainClassName = "dk.sdu.cloud.sync.mounter.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":storage-service:api"))
            implementation("net.java.dev.jna:jna:5.8.0")
        }
    }
}