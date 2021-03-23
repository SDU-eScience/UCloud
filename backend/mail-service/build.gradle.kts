version = "0.4.2"

application {
    mainClassName = "dk.sdu.cloud.mail.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation("com.sun.mail:javax.mail:1.5.5")
            implementation(project(":slack-service:api"))
        }
    }
}

