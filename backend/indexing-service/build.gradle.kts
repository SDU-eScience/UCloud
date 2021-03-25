version = "1.19.8"

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
            implementation("mbuhot:eskotlin:0.7.0") {
                exclude(group="org.elasticsearch", module="elasticsearch")
            }
        }
    }
}
