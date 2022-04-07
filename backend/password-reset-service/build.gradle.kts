version = rootProject.file("./version.txt").readText().trim()

application {
    mainClassName = "dk.sdu.cloud.password.reset.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":mail-service:api"))
        }
    }
}