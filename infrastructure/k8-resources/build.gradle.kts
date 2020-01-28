plugins {
    kotlin("jvm") version "1.3.61"
    `maven-publish`
}

group = "dk.sdu.cloud"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.fabric8:kubernetes-client:4.6.4")
}

publishing {
    publications {
        create<MavenPublication>("lib") {
            from(components["java"])
        }
    }
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}
