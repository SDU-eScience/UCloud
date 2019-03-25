#!/usr/bin/env kscript

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

object Versions {
    val GradleBootstrap = "v0.2.8"
    val AuthAPI = "1.21.0"
    val ServiceCommon = "1.3.1"
}

if (args.size != 1) {
    println("Usage: create_service <serviceName>")
    println("serviceName: The name of the service. Multi word services should be separated by a '-'.")
    println()
    println("Example: create_service file-trash")
    exitProcess(1)
}

val templateDirectory = File("service-template")

if (!templateDirectory.exists()) { println("This script should be run from the repo's root directory.")
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
    println("Generating log4j2 config")
    val mainResources = File(serviceDirectory, "src/main/resources")
    val testResources = File(serviceDirectory, "src/test/resources")

    Files.createDirectories(mainResources.toPath())
    Files.createDirectories(testResources.toPath())

    listOf(File(mainResources, "log4j2.xml"), File(testResources, "log4j2.xml")).forEach {
        it.writeText(
            //language=xml
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <Configuration status="WARN">
                    <Appenders>
                        <Console name="Console" target="SYSTEM_OUT">
                            <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level (%X{request-id}) %C{1.1} - %msg%n"/>
                        </Console>
                    </Appenders>
                    <Loggers>
                        <Root level="debug">
                            <AppenderRef ref="Console"/>
                        </Root>
                        <Logger name="org.apache.kafka" level="warn" additivity="false">
                            <AppenderRef ref="Console"/>
                        </Logger>
                        <Logger name="org.apache.zookeeper" level="warn" additivity="false">
                            <AppenderRef ref="Console"/>
                        </Logger>
                        <Logger name="org.asynchttpclient" level="warn" additivity="false">
                            <AppenderRef ref="Console"/>
                        </Logger>
                        <Logger name="io.netty" level="info" additivity="false">
                            <AppenderRef ref="Console"/>
                        </Logger>
                        <Logger name="dk.sdu.cloud.service.EventProducer" level="info" additivity="false">
                            <AppenderRef ref="Console"/>
                        </Logger>
                        <Logger name="org.hibernate" level="info" additivity="false">
                            <AppenderRef ref="Console"/>
                        </Logger>
                        <Logger name="com.zaxxer.hikari" level="info" additivity="false">
                            <AppenderRef ref="Console"/>
                        </Logger>
                        <Logger name="io.mockk.impl" level="info" additivity="false">
                            <AppenderRef ref="Console"/>
                        </Logger>
                    </Loggers>
                </Configuration>

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
               publishHTML([allowmissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: '{{ serviceName }}-service/build/reports/detekt', reportFiles: 'detekt.html', reportName: '{{ serviceName }}-service-detekt-Report', reportTitles: ''])
             }
             try {
               stage('test $serviceName-service') {
                 sh '''cd $serviceName-service
                 ./gradlew test'''
               }
             } catch (e) {
               echo "Test FAILED"
               return currentBuild.result ?: 'UNSTABLE'
             }
             return currentBuild.result ?: 'SUCCESS'
           }

           return this

        """.trimIndent()
    )
}

run {
    println("Generating Dockerfile")
    val dollar = "$"

    File(serviceDirectory, "Dockerfile").writeText(
        //language=Dockerfile
        """
            # Dockerfile template for production containers of microservices

            FROM alpine:3.8 as build-dependencies

            # Add basic dependencies. Right now this is just JDK8
            ENV JAVA_HOME=/usr/lib/jvm/default-jvm \
                PATH=${dollar}PATH:${dollar}JAVA_HOME/jre/bin:${dollar}JAVA_HOME/bin

            RUN apk add --no-cache openjdk8

            RUN apk add --no-cache curl zip bash
            RUN curl -s "https://get.sdkman.io" | bash
            SHELL ["/bin/bash", "-c"]
            RUN source ${dollar}HOME/.bashrc ; sdk install gradle 4.10.2

            #
            # Development container
            #
            FROM build-dependencies as development

            VOLUME /usr/src/app
            WORKDIR /usr/src/app

            #
            # The intermediate container will contain secrets. These are entirely removed
            # from the production build.
            #
            FROM build-dependencies as build-production-ready

            # The name of the service. Used to identify the executable
            ENV SERVICE_NAME=$serviceName-service

            # Copy application code into /usr/src/app
            COPY . /usr/src/app/
            WORKDIR /usr/src/app/

            # Copy gradle.properties
            ARG GRADLE_PROPS
            RUN mkdir /root/.gradle/ && \
                echo "${dollar}{GRADLE_PROPS}" > /root/.gradle/gradle.properties

            RUN source ${dollar}HOME/.bashrc && \
                set -x && \
                gradle distTar && \
                (mkdir -p /opt/service || true) && \
                cp build/distributions/*.tar /opt/service.tar && \
                cd /opt/service && \
                tar xvf ../service.tar --strip-components=1 && \
                mv /opt/service/bin/${dollar}{SERVICE_NAME} /opt/service/bin/service

            #
            # Production container is only capable of running the code
            #

            FROM alpine:3.8 as production

            ENV JAVA_HOME=/usr/lib/jvm/default-jvm \
                PATH=${dollar}PATH:${dollar}JAVA_HOME/jre/bin:${dollar}JAVA_HOME/bin

            RUN apk add --no-cache openjdk8-jre

            COPY --from=build-production-ready /opt/service/ /opt/service/

            CMD ["/opt/service/bin/service"]

        """.trimIndent()
    )
}

run {
    println("Generating k8s resources")
    val dir = File(serviceDirectory, "k8")
    dir.mkdir()

    run {
        println("Generating deployment.yml")
        File(dir, "deployment.yml").writeText(
            //language=yml
            """
                ---
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: $serviceName
                  labels:
                    app: $serviceName
                spec:
                  replicas: 1
                  selector:
                    matchLabels:
                      app: $serviceName
                  template:
                    metadata:
                      labels:
                        app: $serviceName
                    spec:
                      containers:
                      - name: $serviceName
                        image: registry.cloud.sdu.dk/sdu-cloud/$serviceName-service:0.1.0
                        command:
                        - /opt/service/bin/service
                        - --config-dir
                        - /etc/refresh-token
                        - --config-dir
                        - /etc/token-validation
                        - --config-dir
                        - /etc/psql
                        volumeMounts:
                        - mountPath: /etc/refresh-token
                          name: refresh-token
                        - mountPath: /etc/token-validation
                          name: token-validation
                        - mountPath: /etc/psql
                          name: $serviceName-psql
                        env:
                        - name: POD_IP
                          valueFrom:
                            fieldRef:
                              fieldPath: status.podIP

                      volumes:
                      - name: refresh-token
                        secret:
                          optional: false
                          secretName: $serviceName-refresh-token
                      - name: token-validation
                        configMap:
                          name: token-validation
                      - name: $serviceName-psql
                        secret:
                          optional: false
                          secretName: $serviceName-psql

                      imagePullSecrets:
                        - name: esci-docker

            """.trimIndent()
        )
    }

    run {
        println("Generating service.yml")
        File(dir, "service.yml").writeText(
            //language=yaml
            """
                ---
                apiVersion: v1
                kind: Service
                metadata:
                  name: $serviceName
                  annotations:
                    getambassador.io/config: |
                      ---
                      apiVersion: ambassador/v0
                      kind: Mapping
                      name: $serviceName
                      prefix: ^/api/${serviceNameWords.joinToString("/")}(/.*)?${'$'}
                      prefix_regex: true
                      service: $serviceName:8080
                      rewrite: ""

                spec:
                  clusterIP: None
                  type: ClusterIP
                  ports:
                  - name: web
                    port: 8080
                    protocol: TCP
                    targetPort: 8080
                  selector:
                    app: $serviceName

            """.trimIndent()
        )
    }

    run {
        println("Generating job-migrate-db.yml")
        File(dir, "job-migrate-db.yml").writeText(
            //language=yaml
            """
                ---
                apiVersion: batch/v1
                kind: Job

                metadata:
                  name: $serviceName-migration

                spec:
                  template:
                    metadata:
                      name: $serviceName-migration

                    spec:
                      containers:
                        - name: $serviceName-service
                          image: registry.cloud.sdu.dk/sdu-cloud/$serviceName-service:0.1.0
                          args:
                          - /opt/service/bin/service
                          - --config-dir
                          - /etc/refresh-token
                          - --config-dir
                          - /etc/token-validation
                          - --config-dir
                          - /etc/psql
                          - --run-script
                          - migrate-db
                          volumeMounts:
                          - mountPath: /etc/refresh-token
                            name: refresh-token
                          - mountPath: /etc/token-validation
                            name: token-validation
                          - mountPath: /etc/psql
                            name: $serviceName-psql

                      volumes:
                      - name: refresh-token
                        secret:
                          optional: false
                          secretName: $serviceName-refresh-token
                      - name: token-validation
                        configMap:
                          name: token-validation
                          defaultMode: 420
                      - name: $serviceName-psql
                        secret:
                          optional: false
                          secretName: $serviceName-psql

                      restartPolicy: Never
                      imagePullSecrets:
                      - name: esci-docker

            """.trimIndent()
        )
    }
}
