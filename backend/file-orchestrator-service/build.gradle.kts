version = rootProject.file("./version.txt").readText().trim()

application {
    mainClass.set("dk.sdu.cloud.file.orchestrator.MainKt")
}

kotlin.sourceSets {
    val main by getting {
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
