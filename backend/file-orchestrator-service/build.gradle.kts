version = "0.1.0"

application {
    mainClassName = "dk.sdu.cloud.file.orchestrator.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":accounting-service:api"))
            implementation(project(":notification-service:api"))
            implementation("com.github.java-json-tools:json-schema-validator:2.2.14")
        }
    }
}
