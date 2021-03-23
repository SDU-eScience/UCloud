version = "0.4.2"

application {
    mainClassName = "dk.sdu.cloud.webdav.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":storage-service:api"))
            implementation(project(":project-service:api"))
            implementation(project(":project-repository-service:api"))
            implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.10.0.pr3")
        }
    }
}
