#!/usr/bin/env kscript

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

object Versions {
    val GradleBootstrap = "v0.2.24"
    val AuthAPI = "1.27.0"
    val ServiceCommon = "1.12.0"
    val K8Resources = "0.1.0"
}

if (args.size != 1) {
    println("Usage: create_service <serviceName>")
    println("serviceName: The name of the service. Multi word services should be separated by a '-'.")
    println()
    println("Example: create_service file-trash")
    exitProcess(1)
}

val templateDirectory = File("service-template")

if (!templateDirectory.exists()) {
    println("This script should be run from the repo's root directory.")
    exitProcess(1)
}

val serviceName = args.first().replace("-service", "")
val serviceNameWords = serviceName.split("-")
val packageName = "dk.sdu.cloud.${serviceNameWords.joinToString(".")}"
val titleServiceName = serviceNameWords.map { it.capitalize() }.joinToString("")
if (serviceName.contains(Regex("[ .,0-9]"))) {
    println("Bad service name. Matches regex: '[ .,0-9]'")
    exitProcess(1)
}

val serviceDirectory = File(serviceName + "-service")
if (serviceDirectory.exists()) {
    println("Directory at ${serviceDirectory.absolutePath} already exists!")
    exitProcess(1)
}

Files.walk(templateDirectory.toPath()).forEach {
    val relativePath = it.toFile().relativeTo(templateDirectory).path
    val target = File(serviceDirectory, relativePath)
    println("Copying $relativePath")
    Files.copy(it, target.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
}

run {
    println("Generating src/ directory")

    println("Package name: $packageName")
    val mainDirectory = File(serviceDirectory, "src/main/kotlin/" + packageName.replace(".", "/"))
    Files.createDirectories(mainDirectory.toPath())
    Files.createDirectories(File(serviceDirectory, "src/test/kotlin/" + packageName.replace(".", "/")).toPath())

    val packagesToCreate = listOf("api", "rpc", "services", "processors")
    packagesToCreate.forEach { File(mainDirectory, it).mkdir() }

    run {
        println("Generating Main.kt")
        val mainKt = File(mainDirectory, "Main.kt")
        mainKt.writeText(
            //language=kotlin
            """
            package $packageName

            import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
            import $packageName.api.${titleServiceName}ServiceDescription
            import dk.sdu.cloud.micro.*

            fun main(args: Array<String>) {
                val micro = Micro().apply {
                    initWithDefaultFeatures(${titleServiceName}ServiceDescription, args)
                    install(HibernateFeature)
                    install(RefreshingJWTCloudFeature)
                    install(HealthCheckFeature)
                }

                if (micro.runScriptHandler()) return

                Server(micro).start()
            }

        """.trimIndent()
        )
    }

    run {
        println("Generating Server.kt")
        val serverKt = File(mainDirectory, "Server.kt")
        serverKt.writeText(
            //language=kotlin
            """
                package $packageName

                import dk.sdu.cloud.micro.*
                import dk.sdu.cloud.service.*
                import $packageName.rpc.*

                class Server(override val micro: Micro) : CommonServer {
                    override val log = logger()

                    override fun start() {
                        with(micro.server) {
                            configureControllers(
                                ${titleServiceName}Controller()
                            )
                        }

                        startServices()
                    }

                    override fun stop() {
                        super.stop()
                    }
                }

            """.trimIndent()
        )
    }

    run {
        println("Generating example RPC interface")
        File(mainDirectory, "api/${titleServiceName}Descriptions.kt").writeText(
            //language=kotlin
            """
                package $packageName.api

                import dk.sdu.cloud.AccessRight
                import dk.sdu.cloud.CommonErrorMessage
                import dk.sdu.cloud.calls.CallDescriptionContainer
                import dk.sdu.cloud.calls.auth
                import dk.sdu.cloud.calls.bindEntireRequestFromBody
                import dk.sdu.cloud.calls.call
                import dk.sdu.cloud.calls.http
                import io.ktor.http.HttpMethod

                data class ExampleRequest(val message: String)
                data class ExampleResponse(val echo: String)

                object ${titleServiceName}Descriptions : CallDescriptionContainer("${serviceName.replace('-', '.')}") {
                    val baseContext = "/api/${serviceNameWords.joinToString("/")}"

                    val example = call<ExampleRequest, ExampleResponse, CommonErrorMessage>("example") {
                        auth {
                            access = AccessRight.READ
                        }

                        http {
                            method = HttpMethod.Post

                            path {
                                using(baseContext)
                                +"example"
                            }

                            body { bindEntireRequestFromBody() }
                        }
                    }
                }

            """.trimIndent()
        )
    }

    run {
        println("Generating example controller")
        File(mainDirectory, "rpc/${titleServiceName}Controller.kt").writeText(
            //language=kotlin
            """
                package $packageName.rpc

                import dk.sdu.cloud.CommonErrorMessage
                import $packageName.api.*
                import dk.sdu.cloud.service.Controller
                import dk.sdu.cloud.calls.server.RpcServer
                import dk.sdu.cloud.calls.server.securityPrincipal
                import dk.sdu.cloud.service.Loggable

                class ${titleServiceName}Controller : Controller {
                    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
                        implement(${titleServiceName}Descriptions.example) {
                            val user = ctx.securityPrincipal.username
                            log.info("We automatically log calls and user (but this is how you do it ${'$'}user")

                            ok(ExampleResponse(request.message))
                        }
                        return@configure
                    }

                    companion object : Loggable {
                        override val log = logger()
                    }
                }
            """.trimIndent()
        )
    }
}

run {
    println("Generating Gradle files")

    run {
        println("Generating build.gradle")
        val file = File(serviceDirectory, "build.gradle")
        val dollar = "$"
        file.writeText(
            //language=gradle
            """
                group 'dk.sdu.cloud'
                version '0.1.0'

                apply plugin: 'application'
                mainClassName = "$packageName.MainKt"

                apply from: "https://raw.githubusercontent.com/SDU-eScience/GradleBootstrap/${Versions.GradleBootstrap}/sdu-cloud.gradle"

                buildscript {
                    ext.serviceCommonVersion = "${Versions.ServiceCommon}"
                    ext.authApiVersion = "${Versions.AuthAPI}"
                }

                repositories {
                    jcenter()
                }

                dependencies {
                    compile "dk.sdu.cloud:service-common:${dollar}serviceCommonVersion"
                    compile "dk.sdu.cloud:auth-api:${dollar}authApiVersion"
                    compile "dk.sdu.cloud:service-common-test:${dollar}serviceCommonVersion"
                }

                sduCloud.createTasksForApiJar("${serviceName.replace('-', '.')}", [])

            """.trimIndent()
        )
    }

    run {
        println("Generating settings.gradle")
        File(serviceDirectory, "settings.gradle").writeText(
            //language=gradle
            """
                rootProject.name = '$serviceName-service'
            """.trimIndent()
        )
    }
}


run {
    println("Generating Jenkinsfile")

    File(serviceDirectory, "Jenkinsfile").writeText(
        //language=groovy
        """
           def initialize() {
             try {
               stage('build $serviceName-service') {
                 sh '''cd $serviceName-service
                 ./gradlew clean
                 ./gradlew build -x test'''
               }
             } catch (e) {
               echo "Build Failed"
               return currentBuild.result ?: 'FAILURE'
             } finally {
               publishHTML([allowmissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: '$serviceName-service/build/reports/detekt', reportFiles: 'detekt.html', reportName: '$serviceName-service-detekt-Report', reportTitles: ''])
             }
             try {
               stage('test $serviceName-service') {
                 sh '''cd $serviceName-service
                 ./gradlew test'''
               }
             } catch (e) {
               echo "Test FAILED"
               return 'UNSTABLE'
             }
             return 'SUCCESS'
           }

           return this

        """.trimIndent()
    )
}

run {
    println("Generating service.yml")
    File(serviceDirectory, "service.yml").writeText(
        """
            ---
            name: $serviceName

            namespaces:
            - ${serviceName.replace('-', '.')}

            dependencies: []

        """.trimIndent()
    )
}

run {
    println("Generating k8.kts")
    File(serviceDirectory, "k8.kts").writeText(
        """
            //DEPS dk.sdu.cloud:k8-resources:${Versions.K8Resources}
            package dk.sdu.cloud.k8

            bundle {
                name = "$serviceName"
                version = "0.1.0"

                withAmbassador {}

                val deployment = withDeployment {
                    deployment.spec.replicas = 2
                }

                withPostgresMigration(deployment)
            }
        """.trimIndent()
    )
}
