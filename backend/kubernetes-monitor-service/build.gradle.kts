version = "0.2.7"

application {
    mainClassName = "dk.sdu.cloud.kubernetes.monitor.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation("io.fabric8:kubernetes-client:5.2.1")
            implementation(project(":slack-service:api"))
        }
    }
}