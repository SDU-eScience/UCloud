dependencies {
    val ktorVersion = "1.2.3"

    api(project(":service-common"))

    api(group = "com.h2database", name = "h2", version = "1.4.197")
    api(group = "junit", name = "junit", version = "4.12")

    api("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
    api("io.mockk:mockk:1.9.3")
}
