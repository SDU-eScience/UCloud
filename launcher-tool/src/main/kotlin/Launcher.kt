package dk.sdu.cloud

import de.codeshelf.consoleui.elements.ConfirmChoice
import de.codeshelf.consoleui.prompt.CheckboxResult
import de.codeshelf.consoleui.prompt.ConfirmResult
import de.codeshelf.consoleui.prompt.ConsolePrompt
import de.codeshelf.consoleui.prompt.InputResult
import de.codeshelf.consoleui.prompt.ListResult
import de.codeshelf.consoleui.prompt.builder.ListPromptBuilder
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
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

// NOTE(Dan): These are all directories which used to live in `.compose`. Don't allow anyone to pick from this list.
val blacklistedEnvNames = setOf(
    "postgres",
    "passwd",
    "home",
    "cluster-home",
    "im-config"
)

fun selectOrCreateEnvironment(baseDir: File): String {
    val alternativeEnvironments = (baseDir.listFiles() ?: emptyArray()).filter {
        it.isDirectory && it.name !in blacklistedEnvNames
    }
    if (alternativeEnvironments.isNotEmpty()) {
        val menu = object : Menu("Select an environment") {
            init {
                for (env in alternativeEnvironments) {
                    item(env.absolutePath, env.name)
                }
            }

            val createNew = item("new", "Create new environment")
        }

        val selected = menu.display(prompt)
        if (selected != menu.createNew) {
            return selected.name.substringAfterLast('/')
        }
    }

    val builder = prompt.promptBuilder
    builder
        .createInputPrompt()
        .name("selector")
        .message("Select a name for your environment")
        .defaultValue("default")
        .addPrompt()

    val newEnvironment = (prompt.prompt(builder.build()).values.single() as InputResult).input!!
    if (newEnvironment in blacklistedEnvNames) {
        println("Illegal name. Try a different one.")
        exitProcess(1)
    }

    return newEnvironment.substringAfterLast('/')
}

val prompt = ConsolePrompt()
lateinit var compose: DockerCompose
lateinit var currentEnvironment: File

fun main(args: Array<String>) {
    try {
        val postExecPath = args.getOrNull(0) ?: error("Bad invocation")

        AnsiConsole.systemInstall()

        val repoRoot = run {
            when {
                File(".git").exists() -> File(".")
                File("../.git").exists() -> File("..")
                else -> error("Unable to determine repository root. Please run this script from the root of the repository.")
            }
        }.absoluteFile.normalize()

        val version = runCatching { File(repoRoot, "./backend/version.txt").readText() }.getOrNull()?.trim() ?: ""

        println("UCloud $version - Launcher tool")
        println(CharArray(TerminalFactory.get().width) { '-' }.concatToString())

        val baseDir = File(repoRoot, ".compose").also { it.mkdirs() }
        val currentEnvironmentName = runCatching { File(baseDir, "current.txt").readText() }.getOrNull()
        var env = if (currentEnvironmentName == null) {
            null
        } else {
            runCatching { File(baseDir, currentEnvironmentName).takeIf { it.exists() } }.getOrNull()
        }

        if (env == null) {
            println("No active environment detected!")
            env = File(baseDir, selectOrCreateEnvironment(baseDir)).also { it.mkdirs() }
        } else {
            println(ansi().render("Active environment: ").bold().render(env.name).boldOff())
            println()
        }

        val isNewEnvironment = currentEnvironmentName == null
        File(baseDir, "current.txt").writeText(env.name)

        val compose = findCompose()

        currentEnvironment = env
        dk.sdu.cloud.compose = compose

        val (psText, failureText) = compose.ps(env).executeToText()
        if (psText == null) {
            println("Unable to start docker compose in ${env.absolutePath}!")
            println()
            println(failureText)
            println()
            println("The error message above we got from docker compose. If this isn't helpful, " +
                "then try deleting this directory: ${env.absolutePath}.")
            exitProcess(1)
        }

        val psLines =
            psText.lines().filter { !it.trim().startsWith("name", ignoreCase = true) && !it.trim().startsWith("---") }

        generateComposeFile()

        if (isNewEnvironment || psLines.size <= 1) {
            val shouldStart = confirm(
                prompt,
                "The environment '${env.name}' is not running. Do you want to start it?",
                default = true
            )

            if (!shouldStart) return

            startCluster(compose, env)
        }

        when (TopLevelMenu.display(prompt)) {
            TopLevelMenu.openUserInterface -> {
                val address = serviceByName(ServiceMenu(requireAddress = true).display(prompt).name).address!!
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    println("Unable to open web-browser. Open this URL in your own browser:")
                    println(address)
                    return
                } else {
                    Desktop.getDesktop().browse(URI(address))
                }
            }

            TopLevelMenu.openLogs -> {
                val item = ServiceMenu(requireExec = true).display(prompt)
                val service = serviceByName(item.name)
                if (service.useServiceConvention) {
                    File(postExecPath).writeText(
                        compose.exec(
                            env,
                            item.name,
                            listOf("tail", "-f", "/tmp/service.log")
                        ).toBashScript()
                    )
                } else {
                    File(postExecPath).writeText(compose.logs(env, item.name).toBashScript())
                }
            }

            TopLevelMenu.openShell -> {
                val item = ServiceMenu(requireExec = true).display(prompt)
                File(postExecPath).writeText(
                    compose.exec(env, item.name, listOf("/bin/bash")).toBashScript()
                )
            }

            TopLevelMenu.createProvider -> {
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
                        compose.up(currentEnvironment).executeToText()
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
                            """.trimIndent())
                        ).executeToText()

                        compose.exec(
                            currentEnvironment,
                            provider.name,
                            listOf("sh", "-c", "yes | ucloud products register"),
                            tty = false,
                        ).also {
                            it.deadlineInMillis = 30_000
                        }.executeToText()
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

            TopLevelMenu.services -> {
                val service = serviceByName(ServiceMenu().display(prompt).name)

                when (ServiceActionMenu.display(prompt)) {
                    ServiceActionMenu.start -> {
                        File(postExecPath).writeText(
                            startService(service).toBashScript()
                        )
                    }

                    ServiceActionMenu.stop -> {
                        File(postExecPath).writeText(stopService(service).toBashScript())
                    }

                    ServiceActionMenu.restart -> {
                        File(postExecPath).writeText(
                            stopService(service).toBashScript() + "\n" +
                            startService(service).toBashScript() + "\n"
                        )
                    }
                }
            }

            TopLevelMenu.test -> {
                println("Not yet implemented") // TODO
            }

            TopLevelMenu.environment -> {
                when (EnvironmentMenu.display(prompt)) {
                    EnvironmentMenu.stop -> {
                        LoadingIndicator("Shutting down virtual cluster...").use {
                            compose.down(env).executeToText()
                        }
                    }

                    EnvironmentMenu.restart -> {
                        LoadingIndicator("Shutting down virtual cluster...").use {
                            compose.down(env).executeToText()
                        }
                        startCluster(compose, env)
                    }

                    EnvironmentMenu.delete -> {
                        val shouldDelete = confirm(
                            prompt,
                            "Are you sure you want to permanently delete the environment and all the data?"
                        )

                        if (shouldDelete) {
                            LoadingIndicator("Shutting down virtual cluster...").use {
                                compose.down(env, deleteVolumes = true).executeToText()
                            }

                            LoadingIndicator("Delete files associated with virtual cluster...").use {
                                // NOTE(Dan): Running this in a docker container to make sure we have permissions to
                                // delete the files. This is basically a convoluted way of asking for root permissions
                                // without actually asking for root permissions (we are just asking for the equivalent
                                // through docker)
                                startProcessAndCollectToString(
                                    listOf(
                                        findDocker(),
                                        "run",
                                        "--rm",
                                        "-v",
                                        "${env.parentFile.absolutePath}:/data",
                                        "alpine:3",
                                        "/bin/sh",
                                        "-c",
                                        "rm -rf /data/${env.name}"
                                    )
                                )
                            }
                        }
                    }

                    EnvironmentMenu.status -> {
                        File(postExecPath).writeText(compose.ps(env).toBashScript())
                    }

                    EnvironmentMenu.switch -> {
                        LoadingIndicator("Shutting down virtual cluster...").use {
                            compose.down(env).executeToText()
                        }

                        val env = selectOrCreateEnvironment(baseDir)
                        File(baseDir, env).mkdirs()
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

private fun generateComposeFile() {
    val providers = runCatching { File(currentEnvironment, "providers.txt").readLines() }.getOrNull() ?: emptyList()

    Environment(currentEnvironment.name, PortAllocator.Direct)
        .createComposeFile(
            buildList {
                add(ComposeService.UCloudBackend)
                add(ComposeService.UCloudFrontend)
                add(ComposeService.Gateway)
                for (provider in providers) {
                    add(ComposeService.providerFromName(provider))
                }
            }
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
            add("-X$method")
            add(url)
            add("-H")
            add("Authorization: Bearer $bearer")
            if (body != null) {
                add("-H")
                add("Content-Type: application/json")
                add("-d")
                add(body)
            }
            addAll(opts)
        },
        tty = false
    ).also { it.allowFailure = true }.executeToText().first
}

private fun startProviderService(providerId: String) {
    File(currentEnvironment, "providers.txt").appendText(providerId + "\n")
    generateComposeFile()
    LoadingIndicator("Starting provider services...").use {
        compose.up(currentEnvironment).executeToText()
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

private fun startCluster(compose: DockerCompose, currentEnvironment: File) {
    LoadingIndicator("Starting virtual cluster...").use {
        compose.up(currentEnvironment).executeToText()
    }

    LoadingIndicator("Starting UCloud...").use {
        startService(serviceByName("backend")).executeToText()
    }

    LoadingIndicator("Waiting for UCloud to be ready...").use {
        val cmd = compose.exec(currentEnvironment, "backend", listOf("curl", "http://localhost:8080"), tty = false)
        cmd.allowFailure = true

        for (i in 0 until 100) {
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
        )
    } else {
        return compose.start(currentEnvironment, service.containerName)
    }
}

data class ListItem(val message: String, val name: String)

fun ListPromptBuilder.add(item: ListItem): ListPromptBuilder {
    return newItem(item.name).text(item.message).add()
}

abstract class Menu(val prompt: String) {
    private val items = ArrayList<ListItem>()

    fun item(name: String, message: String): ListItem {
        val result = ListItem(message, name)
        items.add(result)
        return result
    }

    fun display(prompt: ConsolePrompt): ListItem {
        val builder = prompt.promptBuilder
        val b = builder.createListPrompt().message(this.prompt)
        for (item in items) {
            b.add(item)
        }
        b.addPrompt()
        val selectedId = (prompt.prompt(builder.build()).values.single() as ListResult).selectedId
        return items.find { it.name == selectedId } ?: error("Unknown selection")
    }
}

object TopLevelMenu : Menu("Select an item from the menu") {
    val openUserInterface = item("ui", "Open user-interface...")
    val openShell = item("shell", "Open shell to...")
    val openLogs = item("logs", "Open logs...")
    val createProvider = item("providers", "Create provider...")
    val services = item("services", "Manage services...")
    val environment = item("environment", "Manage environment...")
    val test = item("test", "Run a test suite...")
}

object EnvironmentMenu : Menu("Select an action") {
    val status = item("status", "Display current environment status")
    val stop = item("stop", "Stop current environment")
    val restart = item("restart", "Restart current environment")
    val delete = item("delete", "Delete current environment")
    val switch = item("switch", "Switch current environment or create a new one")
}

object ServiceActionMenu : Menu("Select an action") {
    val start = item("start", "Start service")
    val stop = item("stop", "Stop service")
    val restart = item("restart", "Restart service")
}

data class CheckboxItem(
    val name: String,
    val message: String,
    var default: Boolean,
    var disabled: Boolean = false,
    var disabledReason: String = "Unavailable"
)

abstract class MultipleChoiceMenu(val prompt: String) {
    private val items = ArrayList<CheckboxItem>()

    fun item(
        name: String,
        message: String,
        default: Boolean = false,
        disabled: Boolean = false,
        disabledText: String = "Unavailable",
    ): CheckboxItem {
        val item = CheckboxItem(name, message, default, disabled, disabledText)
        items.add(item)
        return item
    }

    fun display(prompt: ConsolePrompt): Set<CheckboxItem> {
        val builder = prompt.promptBuilder
        val b = builder.createCheckboxPrompt().message(this.prompt + " (space to select, enter to finish)")
        for (item in items) {
            b
                .newItem(item.name)
                .text(item.message)
                .checked(item.default)
                .disabledText(if (item.disabled) item.disabledReason else null)
                .add()
        }
        b.addPrompt()

        return (prompt.prompt(builder.build()).values.single() as CheckboxResult)
            .selectedIds.mapNotNull { selection -> items.find { it.name == selection } }.toSet()
    }
}

object CreateProviderMenu : MultipleChoiceMenu("Select the providers you wish to configure") {
    val kubernetes = item("k8", "Kubernetes")
    val slurm = item("slurm", "Slurm")
    val puhuri = item("puhuri", "Puhuri", default = true, disabled = true, disabledText = "Already configured")
    val openstack = item("openstack", "OpenStack", disabled = true)
}

enum class LoadingState {
    IN_PROGRESS,
    SUCCESS,
    FAILURE,
    INFO,
    WARNING
}

class LoadingIndicator(var prompt: String) {
    private lateinit var thread: Thread
    val state = AtomicReference(LoadingState.IN_PROGRESS)
    val spinnerFrames = listOf(
        " ⣾ ", " ⣽ ", " ⣻ ", " ⢿ ", " ⡿ ", " ⣟ ", " ⣯ ", " ⣷ ",
        " ⠁ ", " ⠂ ", " ⠄ ", " ⡀ ", " ⢀ ", " ⠠ ", " ⠐ ", " ⠈ "
    )

    inline fun use(code: () -> Unit) {
        try {
            display()
            code()
            state.set(LoadingState.SUCCESS)
        } catch (ex: Throwable) {
            state.set(LoadingState.FAILURE)
            throw ex
        } finally {
            join()
        }
    }

    fun display() {
        thread = Thread {
            var iteration = 0

            while (true) {
                val current = state.get()
                val symbol = when (current) {
                    LoadingState.IN_PROGRESS -> spinnerFrames[(iteration / 2) % spinnerFrames.size]
                    LoadingState.SUCCESS -> "✅"
                    LoadingState.FAILURE -> "❌"
                    LoadingState.INFO -> "💁"
                    LoadingState.WARNING -> "⚠️"
                    else -> ""
                }

                val message = "[$symbol] $prompt"
                if (iteration == 0) {
                    println(ansi().render(message))
                } else {
                    println(ansi().cursorUp(1).eraseLine().render(message))
                }

                if (current != LoadingState.IN_PROGRESS) break
                Thread.sleep(50)
                iteration += 1
            }
        }.also { it.start() }
    }

    fun join() {
        thread.join()
    }
}

fun confirm(prompt: ConsolePrompt, question: String, default: Boolean? = null): Boolean {
    val builder = prompt.promptBuilder
    val f = builder.createConfirmPromp()
        .name("question")
        .message(question)

    if (default != null) {
        f.defaultValue(if (default) ConfirmChoice.ConfirmationValue.YES else ConfirmChoice.ConfirmationValue.NO)
    }

    f.addPrompt()

    return (prompt.prompt(builder.build()).values.single() as ConfirmResult).confirmed == ConfirmChoice.ConfirmationValue.YES
}
