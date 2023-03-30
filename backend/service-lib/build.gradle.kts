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

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    val jacksonVersion = "2.10.0.pr3"
    val ktorVersion = "2.0.2"

    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")

    api("io.ktor:ktor-client-websockets:$ktorVersion")
    api("io.ktor:ktor-client-cio:$ktorVersion")
    api("io.ktor:ktor-client-core:$ktorVersion")

    api("org.apache.logging.log4j:log4j-api:2.17.1")
    api("org.apache.logging.log4j:log4j-core:2.17.1")
    api("com.auth0:java-jwt:3.8.3")

    testApi(kotlin("test"))
}

kotlin {
    sourceSets {
        val api by creating {
            dependencies {
                api("com.google.flatbuffers:flatbuffers-java:23.3.3")
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
