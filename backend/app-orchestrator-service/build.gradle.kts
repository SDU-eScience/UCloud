version = "2.6.0"

application {
    mainClassName = "dk.sdu.cloud.app.orchestrator.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":app-store-service:api"))
            implementation(project(":accounting-service:api"))
            implementation(project(":mail-service:api"))
            implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.4")
        }
    }
}
