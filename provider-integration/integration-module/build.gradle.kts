plugins {
    kotlin("multiplatform") version "1.6.0"
    kotlin("plugin.serialization") version "1.6.0"
}

group = "dk.sdu.cloud"
version = "2021.3.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven { setUrl("https://maven.pkg.jetbrains.space/public/p/kotlinx-coroutines/maven/") }
    maven { setUrl("https://maven.pkg.jetbrains.space/public/p/ktor/eap/") }
    maven {
        name = "UCloudMaven"
        url = uri("https://mvn.cloud.sdu.dk/releases")
    }
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }


    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "dk.sdu.cloud.main"
            }
        }

        compilations["main"].dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
            implementation("dk.sdu.cloud:integration-module-support:2022.1.4")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.2.1")
//            api("io.ktor:ktor-client-curl:1.6.2-test")
//            api("io.ktor:ktor-client-websockets:1.6.2-test")
//            api("io.ktor:ktor-client-cio:1.6.2-test")
//            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1-new-mm-dev1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
        }

        compilations["main"].cinterops {
            val libjwt by creating {
                includeDirs.allHeaders(File(projectDir, "vendor/libjwt"))
                when (preset) {
                    presets["macosX64"] -> {
                        linkerOpts.addAll(
                            listOf(
                                "-l:",
                                File(projectDir, "vendor/libjwt/macos/libjwt.a").absolutePath
                            )
                        )

                        linkerOpts.addAll(
                            "-L/usr/local/opt/openssl@1.1/lib -lssl -lcrypto -L/usr/local/opt/jansson/lib -ljansson".split(
                                " "
                            )
                        )
                    }

                    presets["linuxX64"] -> {
                    }
                }
            }

            val libsqlite3 by creating {
                includeDirs.allHeaders(File(projectDir, "vendor/libsqlite3"))
            }

            val libucloud by creating {
                includeDirs.allHeaders(File(projectDir, "vendor/libucloud"))
            }


            val libmbedtls by creating {
                includeDirs.allHeaders(File(projectDir, "vendor/libmbedtls"))
            }
        }
    }

    sourceSets {
        val nativeMain by getting
        val nativeTest by getting

        all {
            languageSettings.progressiveMode = true
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
        }
    }
}

kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
    binaries.all {
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += "-Xdisable-phases=EscapeAnalysis"
    }
}

task("buildDebug") {
    dependsOn(allprojects.flatMap { project -> project.tasks.matching { it.name == "linkDebugExecutableNative" } })
}

task("buildRelease") {
    dependsOn(allprojects.flatMap { project -> project.tasks.matching { it.name == "linkReleaseExecutableNative" } })
}
