import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import java.net.URI

group = "org.esciencecloud"
version = "0.1.0"

buildscript {
    var kotlin_version: String by extra

    kotlin_version = "1.2.0"

    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(kotlinModule("gradle-plugin", kotlin_version))
    }

}

apply {
    plugin("kotlin")
    plugin("maven-publish")
}

val kotlin_version: String by extra

repositories {
    mavenCentral()
}

dependencies {
    compile(kotlinModule("stdlib-jdk8", kotlin_version))
    compile(group = "org.apache.zookeeper", name = "zookeeper", version = "3.4.11")
    compile(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "0.19.3")
    compile(group = "com.github.zafarkhaja", name = "java-semver", version = "0.9.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

configure<KotlinProjectExtension> {
    experimental.coroutines = Coroutines.ENABLE
}

configure<PublishingExtension> {
    (publications) {
        "mavenJava"(MavenPublication::class) {
            from(components["java"])
        }
    }

    repositories {
        maven {
            url = URI("https://cloud.sdu.dk/archiva/repository/internal")
            credentials {
                username = properties["eScienceCloudUser"] as String
                password = properties["eScienceCloudPassword"] as String
            }
        }
    }
}
