version = "1.2.0"

application {
    mainClassName = "dk.sdu.cloud.elastic.management.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation("mbuhot:eskotlin:0.7.0")
            implementation(project(":slack-service:api"))
            implementation(project(":auth-service:api"))
        }
    }
}
