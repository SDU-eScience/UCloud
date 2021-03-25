version = "1.6.2"

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
