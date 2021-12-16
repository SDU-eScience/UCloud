version = "0.17.2"

application {
    mainClassName = "dk.sdu.cloud.app.store.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":project-service:api"))
            implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.12.2")
            implementation("org.imgscalr:imgscalr-lib:4.2")
        }
    }
}