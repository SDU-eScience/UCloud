package dk.sdu.cloud

import de.codeshelf.consoleui.prompt.ConsolePrompt
import jline.TerminalFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.fusesource.jansi.Ansi.ansi
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

fun cliHint(invocation: String) {
    println(ansi().reset().render("You can also do this with: '").bold().render("./launcher $invocation").boldOff().render("'"))
}

fun main(args: Array<String>) {
    try {
        val postExecPath = args.getOrNull(0) ?: error("Bad invocation")
        postExecFile = File(postExecPath)

        if (args.getOrNull(1) == "--help") {
            printHelp()
        }

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
        if (psText == null && !shouldStart) {
            println("Unable to start docker compose in ${currentEnvironment}!")
            println()
            println(failureText)
            println()
            println("The error message above we got from docker compose. If this isn't helpful, " +
                "then try deleting this directory: ${currentEnvironment}.")
            exitProcess(1)
        }

        val psLines =
            (psText ?: "").lines().filter { !it.isBlank() && !it.trim().startsWith("name", ignoreCase = true) && !it.trim().startsWith("---") }

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

        cliIntercept(args.drop(1))

        val topLevel = TopLevelMenu()
        when (topLevel.display(prompt)) {
            topLevel.portforward -> {
                cliHint("port-forward")
                Commands.portForward()
            }

            topLevel.openUserInterface -> {
                initializeServiceList()
                Commands.openUserInterface(ServiceMenu(requireAddress = true).display(prompt).name)
            }

            topLevel.openLogs -> {
                initializeServiceList()
                val item = ServiceMenu(requireExec = true).display(prompt)
                cliHint("svc ${item.name} logs")
                Commands.openLogs(item.name)
            }

            topLevel.openShell -> {
                initializeServiceList()
                val item = ServiceMenu(requireExec = true).display(prompt)
                cliHint("svc ${item.name} sh")
                Commands.openShell(item.name)
            }

            topLevel.createProvider -> {
                generateComposeFile()
                syncRepository()
                val selectedProviders = CreateProviderMenu.display(prompt)
                for (provider in selectedProviders) {
                    if (provider.disabled) continue
                    Commands.createProvider(provider.name)
                }
            }

            topLevel.services -> {
                generateComposeFile()
                syncRepository()
                val service = serviceByName(ServiceMenu().display(prompt).name)

                when (ServiceActionMenu.display(prompt)) {
                    ServiceActionMenu.start -> {
                        cliHint("svc ${service.containerName} start")
                        Commands.serviceStart(service.containerName)
                    }

                    ServiceActionMenu.stop -> {
                        cliHint("svc ${service.containerName} stop")
                        Commands.serviceStop(service.containerName)
                    }

                    ServiceActionMenu.restart -> {
                        cliHint("svc ${service.containerName} restart [--follow]")
                        Commands.serviceStop(service.containerName)
                        Commands.serviceStart(service.containerName)
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
                        cliHint("env stop")
                        Commands.environmentStop()
                    }

                    EnvironmentMenu.restart -> {
                        cliHint("env restart")
                        Commands.environmentRestart()
                    }

                    EnvironmentMenu.delete -> {
                        val shouldDelete = confirm(
                            prompt,
                            "Are you sure you want to permanently delete the environment and all the data?"
                        )

                        if (shouldDelete) {
                            cliHint("env delete")
                            Commands.environmentDelete()
                        }
                    }

                    EnvironmentMenu.status -> {
                        cliHint("env status")
                        Commands.environmentStatus()
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

fun stopService(
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

fun initializeServiceList() {
    generateComposeFile(doWriteFile = false)
}

fun generateComposeFile(doWriteFile: Boolean = true) {
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

fun callService(
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

fun startProviderService(providerId: String) {
    addProvider(providerId)
    generateComposeFile()
    LoadingIndicator("Starting provider services...").use {
        compose.up(currentEnvironment, noRecreate = true).executeToText()
    }
}

data class ProviderCredentials(val publicKey: String, val refreshToken: String, val projectId: String)
fun registerProvider(providerId: String, domain: String, port: Int): ProviderCredentials {
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

fun fetchAccessToken(): String {
    val tokenJson = callService("backend", "POST", "http://localhost:8080/auth/refresh", "theverysecretadmintoken")
        ?: error("Failed to contact UCloud/Core backend. Check to see if the backend service is running.")
    val accessToken = defaultMapper.decodeFromString(AccessTokenWrapper.serializer(), tokenJson).accessToken
    return accessToken
}

val defaultMapper = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun startCluster(compose: DockerCompose, noRecreate: Boolean) {
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

fun startService(
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
