version = "1.4.9"

application {
    mainClassName = "dk.sdu.cloud.alerting.MainKt"
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