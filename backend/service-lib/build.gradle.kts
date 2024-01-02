import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")
}

repositories {
    jcenter()
    mavenCentral()
    maven { setUrl("https://maven.pkg.jetbrains.space/public/p/kotlinx-coroutines/maven/") }
    maven { setUrl("https://maven.pkg.jetbrains.space/public/p/ktor/eap/") }
}

dependencies {
    val ktorVersion = "2.3.0"

    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")

    api("io.ktor:ktor-client-websockets:$ktorVersion")
    api("io.ktor:ktor-client-cio:$ktorVersion")
    api("io.ktor:ktor-client-core:$ktorVersion")

    api("org.apache.logging.log4j:log4j-api:2.20.0")
    api("org.apache.logging.log4j:log4j-core:2.20.0")
    api("com.auth0:java-jwt:3.19.4")

    val prometheusVersion = "0.16.0"
    api("io.prometheus:simpleclient:$prometheusVersion")
    api("io.prometheus:simpleclient_common:$prometheusVersion")

    testApi(kotlin("test"))
}

kotlin {
    sourceSets {
        val api by creating {
            dependencies {
            }
        }

        val main by getting {
            dependencies {
                dependsOn(api)
            }
        }

        all {
            languageSettings.enableLanguageFeature("InlineClasses")
            languageSettings.progressiveMode = true
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}

version = rootProject.file("./version.txt").readText().trim()
extensions.configure<PublishingExtension>("publishing") {
    repositories {
        maven {
            mavenLocal()

            maven {
                name = "UCloudMaven"
                url = uri("https://mvn.cloud.sdu.dk/releases")
                credentials {
                    username = (project.findProperty("ucloud.mvn.username") as? String?)
                        ?: System.getenv("UCLOUD_MVN_USERNAME")
                    password = (project.findProperty("ucloud.mvn.token") as? String?)
                        ?: System.getenv("UCLOUD_MVN_TOKEN")
                }
            }
        }
    }

    publications {
        create<MavenPublication>("api") {
            from(components["java"])
        }

        all {
            if (this is MavenPublication) {
                this.groupId = "dk.sdu.cloud"
                val metadata = artifactId.substringAfterLast("-")
                this.artifactId = "service-lib" + if (metadata == "api") "" else "-$metadata"
            }
        }
    }
}
