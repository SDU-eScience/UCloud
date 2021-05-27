version = "0.3.0-storage0"

application {
    mainClassName = "dk.sdu.cloud.provider.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":project-service:api"))
        }
    }
}
