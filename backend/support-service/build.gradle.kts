version = "1.5.0-rc1"

application {
    mainClassName = "dk.sdu.cloud.support.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":slack-service:api"))
            implementation(project(":mail-service:api"))
        }
    }
}
