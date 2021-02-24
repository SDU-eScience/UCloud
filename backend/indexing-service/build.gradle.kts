version = "1.18.0-rc1"

application {
    mainClassName = "dk.sdu.cloud.indexing.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":accounting-service:api"))
            implementation(project(":storage-service:api"))
            implementation("net.java.dev.jna:jna:5.2.0")
            implementation("mbuhot:eskotlin:0.4.0")
        }
    }
}
