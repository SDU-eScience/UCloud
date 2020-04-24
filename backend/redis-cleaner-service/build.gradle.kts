version = "0.1.10"

application {
    mainClassName = "dk.sdu.cloud.redis.cleaner.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
}
