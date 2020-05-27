dependencies {
    val ktorVersion = "1.2.3"
    val jacksonVersion = "2.10.0.pr3"
    val jasyncVersion = "1.0.12"

    // Redis
    api("io.lettuce:lettuce-core:5.1.6.RELEASE")

    // Serialization

    api("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${jacksonVersion}")
    api(group = "com.fasterxml.jackson.core", name = "jackson-core", version = jacksonVersion)
    api(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = jacksonVersion)
    api(
        group = "com.fasterxml.jackson.module",
        name = "jackson-module-kotlin",
        version = jacksonVersion
    )

    // ktor
    api(group = "io.ktor", name = "ktor-server-core", version = ktorVersion)
    api(group = "io.ktor", name = "ktor-server-netty", version = ktorVersion)
    api(group = "io.ktor", name = "ktor-server-host-common", version = ktorVersion)
    api(group = "io.ktor", name = "ktor-websockets", version = ktorVersion)

    // db
    api(group = "org.postgresql", name = "postgresql", version = "42.2.5")
    api(group = "org.hibernate", name = "hibernate-core", version = "5.4.1.Final")
    api(group = "org.hibernate", name = "hibernate-hikaricp", version = "5.4.1.Final")
    api("org.flywaydb:flyway-core:5.2.4")

    api("com.github.jasync-sql:jasync-common:$jasyncVersion")
    api("com.github.jasync-sql:jasync-postgresql:$jasyncVersion")

    api("eu.infomas:annotation-detector:3.0.5")

    // Client
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.2.2")

    api("io.ktor:ktor-client-core:$ktorVersion")
    api("io.ktor:ktor-client-okhttp:$ktorVersion")
    api("io.ktor:ktor-client-websockets:$ktorVersion")
    api("io.ktor:ktor-client-cio:$ktorVersion")

    // JWTs
    api("com.auth0:java-jwt:3.8.3")

    // Utilities
    api("com.google.guava:guava:27.0.1-jre")

    // Logging
    api(group = "org.apache.logging.log4j", name = "log4j-api", version = "2.12.0")
    api(group = "org.apache.logging.log4j", name = "log4j-slf4j-impl", version = "2.12.0")
    api(group = "org.apache.logging.log4j", name = "log4j-core", version = "2.12.0")

    // Elastic
    api("org.elasticsearch.client:elasticsearch-rest-high-level-client:7.5.0")
    api("net.java.dev.jna:jna:5.2.0")

    // Testing
    testImplementation(group = "com.h2database", name = "h2", version = "1.4.197")
    testImplementation(group = "junit", name = "junit", version = "4.12")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
    testImplementation("io.mockk:mockk:1.9.3")
}

configurations.all {
    resolutionStrategy {
        force("net.java.dev.jna:jna:5.2.0")
    }
}
