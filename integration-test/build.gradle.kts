import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm") version "1.9.10"
}

group = "dk.sdu.cloud"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://mvn.cloud.sdu.dk")
}

dependencies {
     run {
        val version = "2023.4.0-dev.41"
        fun ucloud(module: String) = implementation("dk.sdu.cloud:$module:$version")

        ucloud("file-orchestrator-service-api")
        ucloud("app-orchestrator-service-api")
        ucloud("service-lib-lib")
    }

    implementation("org.slf4j:slf4j-nop:2.0.3")
    testImplementation(kotlin("test"))
}


fun Test.configureTests(filter: String) {
    useJUnitPlatform()
    description = "Runs integration test"
    group = "verification"

    systemProperty("log4j2.configurationFactory", "dk.sdu.cloud.micro.Log4j2ConfigFactory")
    systemProperty("java.io.tmpdir", System.getProperty("java.io.tmpdir"))

    filter {
        isFailOnNoMatchingTests = false
        includeTestsMatching(filter)
    }

    testLogging {
        events(*TestLogEvent.values())
        exceptionFormat = TestExceptionFormat.FULL
        outputs.upToDateWhen { false }
        showExceptions = true
        showCauses = true
        showStackTraces = true

        debug {
            events(*TestLogEvent.values())
            exceptionFormat = TestExceptionFormat.FULL
        }
        info.events = debug.events
        info.exceptionFormat = debug.exceptionFormat

        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor?) {
                // Empty
            }

            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                val size = 80
                if (suite.parent != null) return
                print(
                    buildString {
                        appendln()
                        repeat(size) { append('-') }
                        appendln()
                        appendln(result.resultType.toString())
                        repeat(size) { append('-') }
                        appendln()

                        append(" TESTS:".padEnd(size - result.testCount.toString().length))
                        appendln(result.testCount)
                        append("PASSED:".padEnd(size - result.successfulTestCount.toString().length))
                        appendln(result.successfulTestCount)
                        append("FAILED:".padEnd(size - result.failedTestCount.toString().length))
                        appendln(result.failedTestCount)
                    }
                )
            }

            override fun beforeTest(testDescriptor: TestDescriptor?) {
                // Empty
            }

            override fun afterTest(testDescriptor: TestDescriptor?, result: TestResult?) {
                // Empty
            }
        })
    }
}

task<Test>("integrationTest") {
    configureTests("dk.sdu.cloud.integration.backend.*")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(20)
}
