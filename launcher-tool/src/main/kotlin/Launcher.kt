package dk.sdu.cloud

import de.codeshelf.consoleui.prompt.ConsolePrompt
import jline.TerminalFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.fusesource.jansi.AnsiConsole
import java.awt.Desktop
import java.io.File
import java.net.URI
import kotlin.random.Random
import kotlin.system.exitProcess

val prompt = ConsolePrompt()
lateinit var compose: DockerCompose
lateinit var commandFactory: ExecutableCommandFactory
lateinit var fileFactory: FileFactory
lateinit var postExecFile: File
lateinit var repoRoot: LocalFile

fun ExecutableCommand(
    args: List<String>,
    workingDir: LFile? = null,
    postProcessor: (result: ProcessResultText) -> String = { it.stdout },
    allowFailure: Boolean = false,
    deadlineInMillis: Long = 1000 * 60 * 5,
): ExecutableCommand = commandFactory.create(args, workingDir, postProcessor, allowFailure, deadlineInMillis)

fun main(args: Array<String>) {
    if (false) {
        LoadingIndicator("No output").use {
            Thread.sleep(2000)
        }
        LoadingIndicator("Two lines").use {
            Thread.sleep(1000)
            printStatus("Line 1")
            printStatus("Line 2")
            Thread.sleep(1000)
        }
        LoadingIndicator("This is a test").use {
            repeat(10) {
                printStatus("This is a status: $it")
                Thread.sleep(500L)
            }
        }
        LoadingIndicator("This is a test with a lot of output in batches").use {
            repeat(3) { outer ->
                repeat(30) {
                    printStatus("This is a status: ${outer * it}")
                }
                Thread.sleep(Random.nextInt(5) * 1000L)
            }
        }
        LoadingIndicator("This is a test with a lot of output").use {
            repeat(5000) {
                printStatus("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA: $it")
                printStatus("BBBBB: $it")
                printStatus("CCCCCCCCCCCCCCC: $it")
                printStatus("DDDDDDDDDDDDDDDDDDDDD: $it")
                Thread.sleep(1)
            }
        }
        return
    }
    try {
        val postExecPath = args.getOrNull(0) ?: error("Bad invocation")
        postExecFile = File(postExecPath)

        AnsiConsole.systemInstall()

        val repoRoot = run {
            when {
                File(".git").exists() -> File(".")
                File("../.git").exists() -> File("..")
                else -> error("Unable to determine repository root. Please run this script from the root of the repository.")
            }
        }.absoluteFile.normalize()

        dk.sdu.cloud.repoRoot = LocalFile(repoRoot.absolutePath)

        val version = runCatching { File(repoRoot, "./backend/version.txt").readText() }.getOrNull()?.trim() ?: ""

        commandFactory = LocalExecutableCommandFactory()
        fileFactory = LocalFileFactory()

        println("UCloud $version - Launcher tool")
        println(CharArray(TerminalFactory.get().width) { '-' }.concatToString())

        val shouldStart = initCurrentEnvironment().shouldStartEnvironment

        val compose = findCompose()
        dk.sdu.cloud.compose = compose

        val (psText, failureText) = compose.ps(currentEnvironment).executeToText()
        if (psText == null) {
            println("Unable to start docker compose in ${currentEnvironment}!")
            println()
            println(failureText)
            println()
            println("The error message above we got from docker compose. If this isn't helpful, " +
                "then try deleting this directory: ${currentEnvironment}.")
            exitProcess(1)
        }

        val psLines =
            psText.lines().filter { !it.isBlank() && !it.trim().startsWith("name", ignoreCase = true) && !it.trim().startsWith("---") }

        if (shouldStart || psLines.size <= 1) {
            generateComposeFile()
            val startConfirmed = confirm(
                prompt,
                "The environment '${currentEnvironment.name}' is not running. Do you want to start it?",
                default = true
            )

            if (!startConfirmed) return

            startCluster(compose, noRecreate = false)
        }

        val topLevel = TopLevelMenu()
        when (topLevel.display(prompt)) {
            topLevel.portforward -> {
                initializeServiceList()
                val ports = (portAllocator as PortAllocator.Remapped).allocatedPorts
                val conn = (commandFactory as RemoteExecutableCommandFactory).connection

                val forward = ports.entries.joinToString(" ") { "-L ${it.key}:localhost:${it.value}" }
                postExecFile.writeText(
                    """
                        echo;
                        echo;
                        echo;
                        echo "Please keep this window running. You will not be able to access any services without it."
                        echo "This window needs to be restarted if you add any new providers or switch environment!"
                        echo;
                        echo "This command requires your local sudo password to enable port forwarding of privileged ports (80 and 443)."
                        echo;
                        echo;
                        echo;
                        sudo -E ssh -F ~/.ssh/config $forward ${conn.username}@${conn.host} sleep inf  
                    """.trimIndent()
                )
            }

            topLevel.openUserInterface -> {
                val address = serviceByName(ServiceMenu(requireAddress = true).display(prompt).name).address!!
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    println("Unable to open web-browser. Open this URL in your own browser:")
                    println(address)
                    return
                } else {
                    Desktop.getDesktop().browse(URI(address))
                }
            }

            topLevel.openLogs -> {
                initializeServiceList()
                val item = ServiceMenu(requireExec = true).display(prompt)
                val service = serviceByName(item.name)
                if (service.useServiceConvention) {
                    postExecFile.writeText(
                        compose.exec(
                            currentEnvironment,
                            item.name,
                            listOf("tail", "-f", "/tmp/service.log")
                        ).toBashScript()
                    )
                } else {
                    postExecFile.writeText(compose.logs(currentEnvironment, item.name).toBashScript())
                }
            }

            topLevel.openShell -> {
                initializeServiceList()
                val item = ServiceMenu(requireExec = true).display(prompt)
                postExecFile.writeText(
                    compose.exec(currentEnvironment, item.name, listOf("/bin/bash")).toBashScript()
                )
            }

            topLevel.createProvider -> {
                generateComposeFile()
                syncRepository()
                val selectedProviders = CreateProviderMenu.display(prompt)
                for (provider in selectedProviders) {
                    if (provider.disabled) continue

                    startProviderService(provider.name)

                    var credentials: ProviderCredentials? = null
                    LoadingIndicator("Registering provider with UCloud/Core").use {
                        credentials = registerProvider(provider.name, provider.name, 8889)
                    }

                    val creds = credentials!!

                    LoadingIndicator("Configuring provider...").use {
                        ComposeService.providerFromName(provider.name).install(creds)
                    }

                    LoadingIndicator("Starting provider...").use {
                        compose.up(currentEnvironment, noRecreate = true).executeToText()
                        startService(serviceByName(provider.name)).executeToText()
                    }

                    LoadingIndicator("Registering products with UCloud/Core").use {
                        compose.exec(
                            currentEnvironment,
                            provider.name,
                            listOf("sh", "-c", """
                                while ! test -e "/var/run/ucloud/ucloud.sock"; do
                                  sleep 1
                                  echo "Waiting for UCloud sock to be ready..."
                                done
                            """.trimIndent()),
                            tty = false,
                        ).streamOutput().executeToText()

                        compose.exec(
                            currentEnvironment,
                            provider.name,
                            listOf("sh", "-c", "yes | ucloud products register"),
                            tty = false,
                        ).also {
                            it.deadlineInMillis = 30_000
                        }.streamOutput().executeToText()
                    }

                    LoadingIndicator("Restarting provider...").use {
                        stopService(serviceByName(provider.name)).executeToText()
                        startService(serviceByName(provider.name)).executeToText()
                    }

                    LoadingIndicator("Granting credits to provider project").use {
                        val accessToken = fetchAccessToken()
                        val productPage = defaultMapper.decodeFromString(
                            JsonObject.serializer(),
                            callService(
                                "backend",
                                "GET",
                                "http://localhost:8080/api/products/browse?filterProvider=${provider.name}&itemsPerPage=250",
                                accessToken
                            ) ?: error("Failed to retrieve products from UCloud")
                        )

                        val productItems = productPage["items"] as JsonArray
                        val productCategories = HashSet<String>()
                        productItems.forEach { item ->
                            productCategories.add(
                                (((item as JsonObject)["category"] as JsonObject)["name"] as JsonPrimitive).content
                            )
                        }

                        productCategories.forEach { category ->
                            callService(
                                "backend",
                                "POST",
                                "http://localhost:8080/api/accounting/rootDeposit",
                                accessToken,
                                //language=json
                                """
                                  {
                                    "items": [
                                      {
                                        "categoryId": { "name": "$category", "provider": "${provider.name}" },
                                        "amount": 50000000000,
                                        "description": "Root deposit",
                                        "recipient": {
                                          "type": "project",
                                          "projectId": "${creds.projectId}"
                                        }
                                      }
                                    ]
                                  }
                                """.trimIndent()
                            )
                        }
                    }
                }
            }

            topLevel.services -> {
                generateComposeFile()
                syncRepository()
                val service = serviceByName(ServiceMenu().display(prompt).name)

                when (ServiceActionMenu.display(prompt)) {
                    ServiceActionMenu.start -> {
                        postExecFile.writeText(
                            startService(service).toBashScript()
                        )
                    }

                    ServiceActionMenu.stop -> {
                        postExecFile.writeText(stopService(service).toBashScript())
                    }

                    ServiceActionMenu.restart -> {
                        postExecFile.writeText(
                            stopService(service).toBashScript() + "\n" +
                            startService(service).toBashScript() + "\n"
                        )
                    }
                }
            }

            topLevel.test -> {
                generateComposeFile()
                syncRepository()
                println("Not yet implemented") // TODO
            }

            topLevel.environment -> {
                generateComposeFile()
                syncRepository()

                when (EnvironmentMenu.display(prompt)) {
                    EnvironmentMenu.stop -> {
                        LoadingIndicator("Shutting down virtual cluster...").use {
                            compose.down(currentEnvironment).streamOutput().executeToText()
                        }
                    }

                    EnvironmentMenu.restart -> {
                        LoadingIndicator("Shutting down virtual cluster...").use {
                            compose.down(currentEnvironment).streamOutput().executeToText()
                        }
                        startCluster(compose, noRecreate = false)
                    }

                    EnvironmentMenu.delete -> {
                        val shouldDelete = confirm(
                            prompt,
                            "Are you sure you want to permanently delete the environment and all the data?"
                        )

                        if (shouldDelete) {
                            LoadingIndicator("Shutting down virtual cluster...").use {
                                compose.down(currentEnvironment, deleteVolumes = true).streamOutput().executeToText()
                            }

                            LoadingIndicator("Deleting files associated with virtual cluster...").use {
                                // NOTE(Dan): Running this in a docker container to make sure we have permissions to
                                // delete the files. This is basically a convoluted way of asking for root permissions
                                // without actually asking for root permissions (we are just asking for the equivalent
                                // through docker)
                                ExecutableCommand(
                                    listOf(
                                        findDocker(),
                                        "run",
                                        "--rm",
                                        "-v",
                                        "${File(currentEnvironment.absolutePath).parentFile.absolutePath}:/data",
                                        "alpine:3",
                                        "/bin/sh",
                                        "-c",
                                        "rm -rf /data/${currentEnvironment.name}"
                                    )
                                ).executeToText()

                                File(localEnvironment.jvmFile.parentFile, "current.txt").delete()
                                localEnvironment.jvmFile.deleteRecursively()
                            }
                        }
                    }

                    EnvironmentMenu.status -> {
                        postExecFile.writeText(compose.ps(currentEnvironment).toBashScript())
                    }

                    EnvironmentMenu.switch -> {
                        LoadingIndicator("Shutting down virtual cluster...").use {
                            compose.down(currentEnvironment).streamOutput().executeToText()
                        }

                        val baseDir = File(localEnvironment.jvmFile, ".compose").also { it.mkdirs() }
                        val env = selectOrCreateEnvironment(baseDir)
                        initIO()
                        currentEnvironment.child("..").child(env).mkdirs()
                        File(baseDir, "current.txt").writeText(env)
                    }
                }
            }
        }
    } finally {
        TerminalFactory.get().restore()
    }
}

private fun stopService(
    service: Service,
): ExecutableCommand {
    if (service.useServiceConvention) {
        return compose.exec(
            currentEnvironment,
            service.containerName,
            listOf("/opt/ucloud/service.sh", "stop"),
            tty = false
        )
    } else {
        return compose.stop(currentEnvironment, service.containerName)
    }
}

private fun initializeServiceList() {
    generateComposeFile(doWriteFile = false)
}

private fun generateComposeFile(doWriteFile: Boolean = true) {
    val providers = listConfiguredProviders()

    Environment(
        currentEnvironment.name,
        currentEnvironment.child("../../"),
        doWriteFile
    ).createComposeFile(
        buildList {
            add(ComposeService.UCloudBackend)
            add(ComposeService.UCloudFrontend)
            add(ComposeService.Gateway)
            for (provider in providers) {
                add(ComposeService.providerFromName(provider))
            }
        },
    )
}

@Serializable
data class AccessTokenWrapper(val accessToken: String)

@Serializable
data class BulkResponse<T>(val responses: List<T>)

@Serializable
data class FindByStringId(val id: String)

private fun callService(
    container: String,
    method: String,
    url: String,
    bearer: String,
    body: String? = null,
    opts: List<String> = emptyList()
): String? {
    return compose.exec(
        currentEnvironment,
        container,
        buildList {
            add("curl")
            add("-ssS")
            add("-X$method")
            add(url)
            add("-H")
            add("Authorization: Bearer $bearer")
            if (body != null) {
                add("-H")
                add("Content-Type: application/json")
                add("-d")
                add(body)
            } else {
                add("-d")
                add("")
            }
            addAll(opts)
        },
        tty = false
    ).allowFailure().executeToText().first
}

private fun startProviderService(providerId: String) {
    addProvider(providerId)
    generateComposeFile()
    LoadingIndicator("Starting provider services...").use {
        compose.up(currentEnvironment, noRecreate = true).executeToText()
    }
}

data class ProviderCredentials(val publicKey: String, val refreshToken: String, val projectId: String)
private fun registerProvider(providerId: String, domain: String, port: Int): ProviderCredentials {
    val accessToken = fetchAccessToken()

    val projectId = defaultMapper.decodeFromString(
        BulkResponse.serializer(FindByStringId.serializer()),
        callService(
            "backend",
            "POST",
            "http://localhost:8080/api/projects/v2",
            accessToken,
            //language=json
            """
              {
                "items": [
                  {
                    "parent": null,
                    "title": "Provider $providerId"
                  }
                ]
              }
            """.trimIndent(),
            opts = listOf(
                "-H", "principal-investigator: user"
            )
        ) ?: error("Project creation failed. Check backend logs.")
    ).responses.single().id

    callService(
        "backend",
        "POST",
        "http://localhost:8080/api/providers",
        accessToken,
        //language=json
        """
          {
            "items": [
              {
                "id": "$providerId",
                "domain": "$domain",
                "https": false,
                "port": $port
              }
            ]
          }
        """.trimIndent(),

        opts = listOf(
            "-H", "Project: $projectId"
        )
    ) ?: error("Provider creation failed. Check backend logs.")

    val providerObject = defaultMapper.decodeFromString(
        JsonObject.serializer(),
        callService(
            "backend",
            "GET",
            "http://localhost:8080/api/providers/browse?filterName=$providerId",
            accessToken,
            opts = listOf(
                "-H", "Project: $projectId"
            )
        ) ?: error("Provider creation failed. Check backend logs")
    )

    val credentials = runCatching {
        val p = ((providerObject["items"] as JsonArray)[0] as JsonObject)
        val publicKey = (p["publicKey"] as JsonPrimitive).content
        val refreshToken = (p["refreshToken"] as JsonPrimitive).content

        ProviderCredentials(publicKey, refreshToken, projectId)
    }.getOrNull() ?: error("Provider creation failed. Check backend logs.")

    return credentials
}

private fun fetchAccessToken(): String {
    val tokenJson = callService("backend", "POST", "http://localhost:8080/auth/refresh", "theverysecretadmintoken")
        ?: error("Failed to contact UCloud/Core backend. Check to see if the backend service is running.")
    val accessToken = defaultMapper.decodeFromString(AccessTokenWrapper.serializer(), tokenJson).accessToken
    return accessToken
}

val defaultMapper = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private fun startCluster(compose: DockerCompose, noRecreate: Boolean) {
    LoadingIndicator("Starting virtual cluster...").use {
        compose.up(currentEnvironment, noRecreate = noRecreate).streamOutput().executeToText()
    }

    LoadingIndicator("Starting UCloud...").use {
        startService(serviceByName("backend")).executeToText()
    }

    LoadingIndicator("Waiting for UCloud to be ready...").use {
        val cmd = compose.exec(currentEnvironment, "backend", listOf("curl", "http://localhost:8080"), tty = false)
        cmd.allowFailure = true

        for (i in 0 until 100) {
            if (i > 20) cmd.streamOutput()
            if (cmd.executeToText().first != null) break
            Thread.sleep(1000)
        }
    }
}

private fun startService(
    service: Service,
): ExecutableCommand {
    if (service.useServiceConvention) {
        return compose.exec(
            currentEnvironment,
            service.containerName,
            listOf("/opt/ucloud/service.sh", "start"),
            tty = false
        ).streamOutput()
    } else {
        return compose.start(currentEnvironment, service.containerName).streamOutput()
    }
}
