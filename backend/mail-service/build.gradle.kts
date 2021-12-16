version = "0.4.10"

application {
    mainClassName = "dk.sdu.cloud.mail.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation("com.sun.mail:javax.mail:1.6.2")
            implementation(project(":slack-service:api"))
        }
    }
}
