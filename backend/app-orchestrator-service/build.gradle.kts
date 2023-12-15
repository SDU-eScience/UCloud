version = rootProject.file("./version.txt").readText().trim()

application {
    mainClassName = "dk.sdu.cloud.app.orchestrator.MainKt"
}

kotlin.sourceSets {
    val main by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":app-store-service:api"))
            implementation(project(":accounting-service:api"))
            implementation(project(":accounting-service:util"))
            implementation(project(":mail-service:api"))
            implementation(project(":notification-service:api"))
            implementation(project(":file-orchestrator-service:api"))
            implementation(project(":file-orchestrator-service:util"))
            implementation(project(":cliff-utils"))
            implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.4")
        }
    }
    val test by getting {
        dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.7.1")
        }
    }
}
