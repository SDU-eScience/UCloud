version = "0.2.0"

application {
    mainClassName = "dk.sdu.cloud.redis.cleaner.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
}
