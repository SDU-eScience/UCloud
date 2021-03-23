version = "1.5.2"

application {
    mainClassName = "dk.sdu.cloud.filesearch.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":storage-service:api"))
            implementation(project(":indexing-service:api"))
            implementation(project(":share-service:api"))
            implementation(project(":project-service:api"))
        }
    }
}
