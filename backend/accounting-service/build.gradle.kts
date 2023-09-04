version = rootProject.file("./version.txt").readText().trim()

application {
    mainClassName = "dk.sdu.cloud.accounting.MainKt"
}

kotlin.sourceSets {
    val main by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":mail-service:api"))
            implementation(project(":notification-service:api"))
            implementation(project(":slack-service:api"))
        }
    }
}
