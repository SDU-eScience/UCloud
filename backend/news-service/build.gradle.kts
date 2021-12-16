version = "0.3.7"

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