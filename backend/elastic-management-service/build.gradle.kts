version = rootProject.file("./version.txt").readText().trim()

application {
    mainClass.set("dk.sdu.cloud.elastic.management.MainKt")
}

kotlin.sourceSets {
    val main by getting {
        dependencies {
            implementation(project(":slack-service:api"))
            implementation(project(":auth-service:api"))
        }
    }
}
