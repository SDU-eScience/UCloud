version = "2021.3.0-alpha11"

application {
    mainClassName = "dk.sdu.cloud.file.orchestrator.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":accounting-service:api"))
            implementation(project(":accounting-service:util"))
            implementation(project(":notification-service:api"))
            implementation(project(":task-service:api"))
            implementation("com.github.java-json-tools:json-schema-validator:2.2.14")
        }
    }
}
