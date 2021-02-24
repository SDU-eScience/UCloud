version = "0.3.0-rc0"

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
