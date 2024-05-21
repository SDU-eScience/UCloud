version = rootProject.file("./version.txt").readText().trim()

application {
    mainClass.set("dk.sdu.cloud.app.store.MainKt")
}

kotlin.sourceSets {
    val main by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":accounting-service:api"))
            implementation(project(":accounting-service:util"))
            implementation(project(":cliff-utils"))
            implementation("com.vladmihalcea:hibernate-types-52:2.4.1")
            implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.4")
            implementation("org.imgscalr:imgscalr-lib:4.2")
        }
    }
}
