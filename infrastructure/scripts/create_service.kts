#!/usr/bin/env kscript

import java.io.File

val service = args.firstOrNull()
    ?: throw IllegalArgumentException("Missing service. Usage: create_service.kts <service>")

val className = service.split("-").joinToString("") { it.capitalize() }
val packageName = service.split("-").joinToString(".") { it.toLowerCase() }

val serviceDir = File("./$service-service")
serviceDir.mkdirs()

File(serviceDir, "README.md").writeText("Empty readme")

File(serviceDir, "build.gradle.kts").writeText(
    """
        version = "0.1.0"
        
        application {
            mainClassName = "dk.sdu.cloud.$packageName.MainKt"
        }
        
        kotlin.sourceSets {
            val jvmMain by getting {
                dependencies {
                    implementation(project(":auth-service:api"))
                }
            }
        }
    """.trimIndent()
)

File(serviceDir, "k8.kts").writeText(
    """
        //DEPS dk.sdu.cloud:k8-resources:0.1.2
        package dk.sdu.cloud.k8
        
        bundle {
            name = "$service"
            version = "0.1.0"
            
            withAmbassador() {}
            
            val deployment = withDeployment {
                deployment.spec.replicas = 2
            }
            
            withPostgresMigration(deployment)
        }
    """.trimIndent()
)

File(serviceDir, "Dockerfile").writeText(
    """
        FROM dreg.cloud.sdu.dk/ucloud/base:0.1.0
        COPY build/service /opt/service/
        CMD ["/opt/service/bin/service"]
    """.trimIndent()
)

val kotlinSrc = File(serviceDir, "src/main/kotlin")
kotlinSrc.mkdirs()
File(serviceDir, "src/main/resources/db/migration").mkdirs()
File(serviceDir, "src/main/resources/db/migration/schema.txt").writeText(service.replace('-', '_'))
File(serviceDir, "src/test/kotlin").mkdirs()
File(serviceDir, "src/test/resources").mkdirs()

val mainPackage = kotlinSrc
mainPackage.mkdirs()

File(mainPackage, "api").mkdirs()
File(mainPackage, "rpc").mkdirs()
File(mainPackage, "services").mkdirs()
File(mainPackage, "Server.kt").writeText(
    """
        package dk.sdu.cloud.$packageName 
        
        import dk.sdu.cloud.micro.Micro
        import dk.sdu.cloud.micro.server
        import dk.sdu.cloud.service.CommonServer
        import dk.sdu.cloud.service.startServices
        
        class Server(override val micro: Micro) : CommonServer {
            override val log = logger()
            
            override fun start() {
                with(micro.server) {
                    // Add controllers below
                    // configureControllers()
                }
                
                startServices()
            }
        }
    """.trimIndent()
)
File(mainPackage, "Main.kt").writeText(
    """
        package dk.sdu.cloud.$packageName
        
        import dk.sdu.cloud.micro.*
        import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
        import dk.sdu.cloud.${packageName}.api.${className}ServiceDescription
        
        object ${className}Service : Service {
            override val description = ${className}ServiceDescription
            
            override fun initializeServer(micro: Micro): CommonServer {
                micro.install(RefreshingJWTCloudFeature)
                return Server(micro)
            }
        }
        
        fun main(args: Array<String>) {
            ${className}Service.runAsStandalone(args)
        }
        
    """.trimIndent()
)

val apiDir = File(serviceDir, "api")
apiDir.mkdirs()
File(apiDir, "build.gradle.kts").writeText("/* Empty */")
val apiSrc = File(apiDir, "src/main/kotlin")
apiSrc.mkdirs()

File(apiSrc, "${className}s.kt").writeText("""
    package dk.sdu.cloud.${packageName}.api
    
    import dk.sdu.cloud.*
    import dk.sdu.cloud.calls.*
    
    object ${className}s : CallDescriptionContainer("$packageName") {
        val baseContext = "/api/${service.split("-").joinToString("/")}"
    }
""".trimIndent())

