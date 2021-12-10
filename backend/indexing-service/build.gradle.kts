version = "1.19.16"

application {
    mainClassName = "dk.sdu.cloud.indexing.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":accounting-service:api"))
            api(project(":storage-service:api"))
            implementation("net.java.dev.jna:jna:5.2.0")
        }
    }
}
