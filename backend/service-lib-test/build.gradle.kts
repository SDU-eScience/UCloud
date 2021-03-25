plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

repositories {
    jcenter()
    mavenCentral()
}

kotlin {
    val jacksonVersion = "2.10.0.pr3"
    val ktorVersion = "1.5.2"
    val jasyncVersion = "1.1.3"

    jvm {
        withJava()
        val main by compilations.getting {
            kotlinOptions {
                jvmTarget = "11"
            }
        }

        val test by compilations.getting {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                api("io.ktor:ktor-client-core:$ktorVersion")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmMain by getting {
            dependencies {
                api(project(":service-lib"))
                api(project(":service-lib-server"))

                api("junit:junit:4.12")

                api("io.ktor:ktor-server-test-host:$ktorVersion") {
                    exclude(group = "ch.qos.logback", module = "logback-classic")
                }
                api("io.mockk:mockk:1.9.3")
                api("io.zonky.test:embedded-postgres:1.2.6")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        all {
            languageSettings.enableLanguageFeature("InlineClasses")
            languageSettings.progressiveMode = true
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("kotlin.time.ExperimentalTime")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            languageSettings.useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}