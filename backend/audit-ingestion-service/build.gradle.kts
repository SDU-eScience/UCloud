version = "0.4.4"

application {
    mainClassName = "dk.sdu.cloud.audit.ingestion.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
        }
    }
}
