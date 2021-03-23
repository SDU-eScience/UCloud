version = "3.5.2"

application {
    mainClassName = "dk.sdu.cloud.project.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":contact-book-service:api"))
            implementation(project(":mail-service:api"))
            implementation(project(":notification-service:api"))
        }
    }
}
