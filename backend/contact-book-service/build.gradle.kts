version = "0.3.0-rc1"

application {
    mainClassName = "dk.sdu.cloud.contact.book.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
        }
    }
}