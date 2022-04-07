version = rootProject.file("./version.txt").readText().trim()

application {
    mainClassName = "dk.sdu.cloud.sync.mounter.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":file-ucloud-service:api"))
            implementation(project(":file-ucloud-service:util"))
            implementation("net.java.dev.jna:jna:5.8.0")
        }
    }
}