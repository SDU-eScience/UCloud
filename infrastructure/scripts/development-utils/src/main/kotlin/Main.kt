//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.9
//DEPS com.fasterxml.jackson.core:jackson-core:2.9.9
//DEPS com.fasterxml.jackson.core:jackson-databind:2.9.9
//DEPS com.fasterxml.jackson.module:jackson-module-kotlin:2.9.9

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.nio.file.Files
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.system.exitProcess

data class ServiceWithDirectory(val service: Service, val directory: File)
data class Service(val name: String, val dependencies: List<String>, val namespaces: List<String>)

val yamlFactory = YAMLFactory()
val yamlMapper = ObjectMapper(yamlFactory).registerKotlinModule()

fun printHelp(): Nothing {
    System.err.println(
        """
             Usage: start-dependencies [args]
             
             This application should be run with sdu-cloud repository as current working directory.
             
             Args:
             
               --exclude <serviceName>: Optional. A service to exclude
               --target <serviceName>: Required. Sets the target of this program. 
               --help: This message.
        """.trimIndent()
    )

    exitProcess(1)
}

fun main(args: Array<String>) {
    val serviceDirs = (File(".").listFiles() ?: emptyArray()).filter { it.name.endsWith("-service") && it.isDirectory }

    var targetService: String? = null
    val excludeList = ArrayList<String>()

    val argStack = LinkedList(args.toMutableList())
    while (argStack.isNotEmpty()) {
        when (argStack.pop()) {
            "--exclude" -> excludeList.add(argStack.pop())
            "--target" -> targetService = argStack.pop()
            "--help" -> printHelp()
        }
    }

    if (targetService == null) printHelp()

    val services = serviceDirs
        .map { File(it, "service.yml") }
        .filter { it.exists() }
        .map { serviceFile ->
            val serviceDirectory = serviceFile.parentFile
            val service = yamlMapper.readValue<Service>(serviceFile)

            ServiceWithDirectory(service, serviceDirectory)
        }

    val targetServiceWithDir = services.find { it.service.name == targetService } ?: panic("No such target service")

    val servicesForConfig =
        (findDependencies("auth", services) +
                findDependencies(targetServiceWithDir.service.namespaces.first(), services)) + targetServiceWithDir

    val servicesToStart = servicesForConfig.filter { it != targetServiceWithDir && it.service.name !in excludeList }

    val configDir = Files.createTempDirectory("config").toFile()
    val overrides = HashMap<String, Any?>()

    var port = 8001
    servicesForConfig.forEach { (service, _) ->
        if (service == targetServiceWithDir.service) return@forEach

        overrides[service.name] = ":$port"
        service.namespaces.forEach { overrides[it] = ":$port" }
        port++
    }

    targetServiceWithDir.let { (service, _) ->
        overrides[service.name] = ":8800"
        service.namespaces.forEach { overrides[it] = ":8800"}
    }

    val frontendDirectory = Files.createTempDirectory("frontend").toFile()
    File(configDir, "overrides.yml").writeText(
        yamlMapper.writeValueAsString(
            mapOf("development" to mapOf(
                "serviceDiscovery" to overrides,
                "frontend" to mapOf("configDir" to frontendDirectory.absolutePath)
            ))
        )
    )

    val authConfigFile = Files.createTempFile("conf", ".yml").toFile().also { it.writeText("refreshToken: None") }

    val userHome = System.getProperty("user.home")
    val sducloudConfig = File(userHome, "sducloud").absolutePath
    File(sducloudConfig, "start-dependencies.yml").writeText("""
        ---
        config:
          additionalDirectories:
          - ${configDir.absolutePath}
    """.trimIndent())
    val scriptBuilder = StringBuilder()

    // Use this to make output pretty https://unix.stackexchange.com/questions/440426/how-to-prefix-any-output-in-a-bash-script
    scriptBuilder.append(
        """
            #!/usr/bin/env bash
            
            prefixed() {
                local prefix="${'$'}1"
                shift
                "$@" > >(sed "s/^/${'$'}prefix: /") 2> >(sed "s/^/${'$'}prefix (err): /" >&2) &
            }
            
            echo "Configuration can be found at ${configDir.absolutePath}"
            echo "The following services will be started"
            ${servicesToStart.joinToString("\n") { "echo \"  ${it.service.name}\"" }}
            echo ""
            
            echo "Press enter to continue. Once all services are running you can press enter again to kill all services."
            read -r line
            
        """.trimIndent()
    )

    val authService = servicesToStart.find { it.service.name == "auth" }
    if (authService != null) {
        val refreshConfig = File(configDir, "refresh.yml").absolutePath
        scriptBuilder.append(
            """
                cd ${authService.directory.absolutePath}
                prefixed "auth" gradle run -PappArgs='["--dev", "--config-dir", "$sducloudConfig", "--config-dir", "${configDir.absolutePath}", "--config", "${authConfigFile.absolutePath}", "--save-config", "$refreshConfig"]'
                
                while [ ! -f $refreshConfig ]; do sleep 1; done
                
            """.trimIndent()
        )
    }

    scriptBuilder.append(
        servicesToStart.filter { it.service.name != "auth" }.joinToString("\n") { (service, dir) ->
            """
                cd ${dir.absolutePath}
                prefixed "${service.name}" gradle run -PappArgs='["--dev", "--config-dir", "$sducloudConfig", "--config-dir", "${configDir.absolutePath}"]'
                
            """.trimIndent()
        }
    )

    scriptBuilder.appendln("prefixed frontend-helper dependencies-frontend-server \"${frontendDirectory.absolutePath}\" < /dev/null")

    scriptBuilder.appendln("read -r line")
    scriptBuilder.appendln("kill `jobs -p`")

    val startScript = File(configDir, "start.sh")
    startScript.writeText(scriptBuilder.toString())
    println(startScript.absolutePath)
}

fun findDependencies(
    target: String,
    pool: List<ServiceWithDirectory>,
    destination: MutableSet<ServiceWithDirectory> = HashSet()
): Set<ServiceWithDirectory> {
    val targetService = pool.find { target in it.service.namespaces } ?: run {
        panic("Could not find service: $target")
    }

    if (targetService in destination) return destination

    destination.add(targetService)
    targetService.service.dependencies.forEach { findDependencies(it, pool, destination) }
    return destination
}

fun panic(message: String): Nothing {
    println(message)
    exitProcess(1)
}

