version = "2022.1.0"

application {
    mainClassName = "dk.sdu.cloud.task.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
        }
    }
}