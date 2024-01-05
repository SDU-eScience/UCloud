version = rootProject.file("./version.txt").readText().trim()

application {
    mainClass.set("dk.sdu.cloud.alerting.MainKt")
}

kotlin.sourceSets {
    val main by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation("io.fabric8:kubernetes-client:4.1.3")
            implementation(project(":slack-service:api"))
        }
    }
}
