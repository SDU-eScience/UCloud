dependencies {
    val ktorVersion = "1.2.3"

    api(project(":service-common"))

    api(group = "junit", name = "junit", version = "4.12")

    api("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
    api("io.mockk:mockk:1.9.3")
    api(group = "io.zonky.test", name = "embedded-postgres", version = "1.2.6")
}
