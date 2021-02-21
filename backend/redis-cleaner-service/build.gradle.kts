version = "0.3.0"

application {
    mainClassName = "dk.sdu.cloud.redis.cleaner.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
}
