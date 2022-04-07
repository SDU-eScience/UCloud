version = rootProject.file("./version.txt").readText().trim()

application {
    mainClassName = "dk.sdu.cloud.redis.cleaner.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
        }
    }
}