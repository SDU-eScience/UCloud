version = rootProject.file("./version.txt").readText().trim()

application {
    mainClass.set("dk.sdu.cloud.password.reset.MainKt")
}

kotlin.sourceSets {
    val main by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":mail-service:api"))
        }
    }
}
