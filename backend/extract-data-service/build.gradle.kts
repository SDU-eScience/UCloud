version = "0.1.3"

application {
    mainClassName = "dk.sdu.cloud.extract.data.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":accounting-service:api"))
        }
    }
}
