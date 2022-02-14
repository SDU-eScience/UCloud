version = "2022.1.7"

application {
    mainClassName = "dk.sdu.cloud.alerting.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation("io.fabric8:kubernetes-client:4.1.3")
            implementation(project(":slack-service:api"))
        }
    }
}