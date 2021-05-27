version = "1.8.0-storage0"

application {
    mainClassName = "dk.sdu.cloud.accounting.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":mail-service:api"))
            implementation(project(":notification-service:api"))
            implementation(project(":contact-book-service:api"))
        }
    }
}
