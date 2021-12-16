version = "1.8.7"

application {
    mainClassName = "dk.sdu.cloud.file.favorite.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":storage-service:api"))
        }
    }
}