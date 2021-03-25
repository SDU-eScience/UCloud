version = "1.7.2"

application {
    mainClassName = "dk.sdu.cloud.accounting.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":project-service:api"))
            implementation(project(":mail-service:api"))
        }
    }
}
