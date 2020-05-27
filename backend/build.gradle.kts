import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    kotlin("jvm") version "1.3.72"
    kotlin("plugin.jpa") version "1.3.72"
    jacoco
}

buildscript {
    repositories {
        jcenter()
        mavenCentral()
        maven { setUrl("https://plugins.gradle.org/m2/") }
    }

    dependencies {
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:0.10.1")
        classpath("org.jetbrains.kotlin:kotlin-noarg:1.3.72")
    }
}

repositories {
    jcenter()
    mavenCentral()
}

// https://guides.gradle.org/creating-multi-project-builds/
// https://docs.gradle.org/current/userguide/multi_project_builds.html
subprojects {
    val groupBuilder = ArrayList<String>()
    var currentProject: Project? = project
    while (currentProject != null && currentProject != rootProject) {
        groupBuilder.add(currentProject.name)
        currentProject = currentProject.parent
    }
    if (groupBuilder.isEmpty()) {
        group = "dk.sdu.cloud"
    } else {
        group = "dk.sdu.cloud." + groupBuilder.reversed().joinToString(".")
    }

    repositories {
        jcenter()
        mavenCentral()
    }

    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "jacoco")
    apply(plugin = "kotlin-jpa")

    if (project.name == "launcher") {
        apply(plugin = "application")
    }

    if (project.name.endsWith("-service")) {
        run {
            val generated = sourceSets.create("generated")

            dependencies {
                implementation(generated.output)
                add("generatedImplementation", project(":service-common"))
            }

            val generateBuildConfig = tasks.register("generateBuildConfig") {
                doFirst {
                    if (project.version == "unspecified") {
                        throw IllegalStateException("Version not set for service: ${project.name} (${project.version})")
                    }

                    run {
                        val src = File(project.projectDir, "src/generated/kotlin")
                        src.mkdirs()
                        val simpleName = project.name.replace("-service", "")
                        val packageName = simpleName.replace("-", ".")
                        val className = simpleName.split("-").joinToString("") { it.capitalize() }

                        File(src, "Description.kt").writeText(
                            """
                            package dk.sdu.cloud.$packageName.api
                            
                            import dk.sdu.cloud.ServiceDescription
                            
                            object ${className}ServiceDescription : ServiceDescription {
                                override val name = "$simpleName"
                                override val version = "${project.version}"
                            }
                        """.trimIndent()
                        )
                    }

                    run {
                        val src = File(project.projectDir, "src/generated/resources")
                        src.mkdirs()

                        File(src, "name.txt").writeText(project.name)
                        File(src, "version.txt").writeText(project.version.toString())
                    }
                }
            }
        }
    }

    tasks.withType<Jar> {
        val name = if (groupBuilder.isEmpty()) {
            "ucloud"
        } else {
            "ucloud-" + groupBuilder.reversed().joinToString("-")
        }

        archiveName = "$name.jar"

        if (project.name.endsWith("-service")) {
            from(sourceSets["generated"].output)
        }
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.72")
    }

    tasks {
        val dokka by getting(DokkaTask::class)
        with(dokka) {
            outputFormat = "html"
            outputDirectory = "$buildDir/javadoc"
        }
    }

    tasks {
        jacoco {
            toolVersion = "0.8.4"
        }

        jacocoTestReport {
            reports {
                xml.isEnabled = true
                html.isEnabled = true
            }
        }

        val test by getting(Task::class)
        test.finalizedBy(jacocoTestReport)
    }

    dependencies {
        implementation(kotlin("stdlib-jdk8"))

        val myApiProject = project.childProjects["api"]
        if (myApiProject != null) {
            implementation(myApiProject)
        }

        if (project.name.endsWith("-service") || project.name == "integration-testing") {
            apply(plugin = "application")
        }

        if (project.name.endsWith("-service") || project.name == "api") {
            implementation(project(":service-common"))
            testImplementation(project(":service-common-test"))
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions.freeCompilerArgs += "-progressive"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }

    tasks.withType<Test>().configureEach {
        systemProperty("log4j2.configurationFactory", "dk.sdu.cloud.micro.Log4j2ConfigFactory")
        systemProperty("java.io.tmpdir", System.getProperty("java.io.tmpdir"))
    }

    val compileKotlin: KotlinCompile by tasks
    compileKotlin.kotlinOptions {
        jvmTarget = "1.8"
    }
    val compileTestKotlin: KotlinCompile by tasks
    compileTestKotlin.kotlinOptions {
        jvmTarget = "1.8"
    }
}

