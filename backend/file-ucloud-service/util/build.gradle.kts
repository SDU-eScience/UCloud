kotlin.sourceSets {
    val main by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":accounting-service:api"))
            api("net.java.dev.jna:jna:5.2.0")
        }
    }
}
