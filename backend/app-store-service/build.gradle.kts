version = "0.16.1"

application {
    mainClassName = "dk.sdu.cloud.app.store.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":accounting-service:api"))
            implementation("com.vladmihalcea:hibernate-types-52:2.4.1")
            implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.4")
            implementation("org.imgscalr:imgscalr-lib:4.2")
        }
    }
}
