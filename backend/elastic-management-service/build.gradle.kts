version = "2021.3.0-alpha1"

application {
    mainClassName = "dk.sdu.cloud.elastic.management.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":slack-service:api"))
            implementation(project(":auth-service:api"))
        }
    }
}
