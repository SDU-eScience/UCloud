version = "1.7.3"

application {
    mainClassName = "dk.sdu.cloud.activity.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":storage-service:api"))
            implementation(project(":file-favorite-service:api"))
            implementation(project(":app-orchestrator-service:api"))
            implementation(project(":app-store-service:api"))
            implementation(project(":share-service:api"))
            implementation(project(":project-repository-service:api"))
        }
    }
}
