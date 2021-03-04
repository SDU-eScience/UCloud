version = "0.2.0"

application {
    mainClassName = "dk.sdu.cloud.news.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
        }
    }
}
