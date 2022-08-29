version = rootProject.file("./version.txt").readText().trim()

application {
    mainClassName = "dk.sdu.cloud.mail.MainKt"
}

kotlin.sourceSets {
    val main by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation("com.sun.mail:javax.mail:1.5.5")
            implementation(project(":slack-service:api"))
        }
    }
}
