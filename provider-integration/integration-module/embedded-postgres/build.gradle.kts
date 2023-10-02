plugins {
    id("java")
}

group = "dk.sdu.cloud"
version = "2022.3.0"

repositories {
    mavenCentral()
}

dependencies {
    run {
        val zonkyVersion = "14.5.0"
        fun pgBinary(platform: String) {
            implementation("io.zonky.test.postgres:embedded-postgres-binaries-$platform:$zonkyVersion")
        }

        pgBinary("windows-amd64")
        pgBinary("darwin-amd64")
        pgBinary("linux-amd64")
        pgBinary("linux-amd64-alpine")
    }

    implementation("org.slf4j:slf4j-api:2.0.1")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("commons-io:commons-io:2.11.0")
    implementation("commons-codec:commons-codec:1.15")
    implementation("org.postgresql:postgresql:42.3.5")
    implementation("org.tukaani:xz:1.9")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
