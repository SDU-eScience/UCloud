version = "1.2.0-rc0"

application {
    mainClassName = "dk.sdu.cloud.elastic.management.MainKt"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation("mbuhot:eskotlin:0.4.0")
        }
    }
}
