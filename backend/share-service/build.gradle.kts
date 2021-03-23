version = "1.10.2"

application {
    mainClassName = "dk.sdu.cloud.share.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":storage-service:api"))
            implementation(project(":notification-service:api"))
            implementation(project(":contact-book-service:api"))
        }
    }
}
