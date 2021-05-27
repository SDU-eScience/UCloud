plugins {
    kotlin("multiplatform") version "1.5.0"
    kotlin("plugin.serialization") version "1.5.0"
}

group = "dk.sdu.cloud"
version = "2021.2.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven {
        name = "UCloud Packages"
        url = uri("https://maven.pkg.github.com/sdu-escience/ucloud")
        credentials {
            val helpText = """
				
				
				
				
				
				Missing GitHub credentials. These are required to pull the packages required for this project. Please 
				create a personal access token here: https://github.com/settings/tokens. This access token require
				the 'read:packages' scope.
				
				With this information you will need to add the following lines to your Gradle properties
				(~/.gradle/gradle.properties):
				
				gpr.user=YOUR_GITHUB_USERNAME
				gpr.token=YOUR_GITHUB_PERSONAL_ACCESS_TOKEN
				
				
				
				
				
			""".trimIndent()
            username = (project.findProperty("gpr.user") as? String?)
                ?: System.getenv("GITHUB_USERNAME") ?: error(helpText)
            password = (project.findProperty("gpr.key") as? String?)
                ?: System.getenv("GITHUB_TOKEN") ?: error(helpText)
        }
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
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
            implementation("dk.sdu.cloud:integration-module-support:2021.2.0-storage0")
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
