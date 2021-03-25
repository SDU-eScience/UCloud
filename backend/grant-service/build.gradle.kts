version = "0.4.2"

application {
    mainClassName = "dk.sdu.cloud.grant.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":project-service:api"))
            implementation(project(":accounting-service:api"))
            implementation(project(":auth-service:api"))
            implementation(project(":mail-service:api"))
            implementation(project(":notification-service:api"))
            implementation(project(":storage-service:api"))
        }
    }
}
