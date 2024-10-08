version = rootProject.file("./version.txt").readText().trim()

application {
    mainClass.set("dk.sdu.cloud.task.MainKt")
}

kotlin.sourceSets {
    val main by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":accounting-service:api"))
            implementation(project(":accounting-service:util"))
        }
    }
}
