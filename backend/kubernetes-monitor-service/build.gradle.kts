version = "0.2.2"

application {
    mainClassName = "dk.sdu.cloud.kubernetes.monitor.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation("io.fabric8:kubernetes-client:4.6.4")
            implementation(project(":slack-service:api"))
        }
    }
}
