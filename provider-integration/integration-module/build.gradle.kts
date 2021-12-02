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
            implementation("dk.sdu.cloud:integration-module-support:2021.3.0-alpha12")
//            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.2.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1-new-mm-dev1")
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

            val libh2o by creating {
                // openssl and libuv
                includeDirs.allHeaders(File(projectDir, "vendor/libh2o"))
                includeDirs.allHeaders(File("/usr/include"))
                includeDirs.allHeaders(File("/usr/include/x86_64-linux-gnu"))
            }

            val libuv by creating {
                includeDirs.allHeaders(File(projectDir, "vendor/libuv"))
                includeDirs.allHeaders(File("/usr/include"))
                includeDirs.allHeaders(File("/usr/include/x86_64-linux-gnu"))
            }

            val libsqlite3 by creating {
                includeDirs.allHeaders(File(projectDir, "vendor/libsqlite3"))
            }

            val libucloud by creating {
                includeDirs.allHeaders(File(projectDir, "vendor/libucloud"))
            }
        }
    }

    sourceSets {
        val nativeMain by getting
        val nativeTest by getting

        all {
            languageSettings.progressiveMode = true
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("kotlin.time.ExperimentalTime")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            languageSettings.useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
        }


    }
}

kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
    binaries.all {
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += "-Xdisable-phases=EscapeAnalysis"
    }
}
