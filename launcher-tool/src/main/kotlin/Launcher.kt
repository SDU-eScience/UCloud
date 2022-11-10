package dk.sdu.cloud

import de.codeshelf.consoleui.elements.ConfirmChoice
import de.codeshelf.consoleui.prompt.CheckboxResult
import de.codeshelf.consoleui.prompt.ConfirmResult
import de.codeshelf.consoleui.prompt.ConsolePrompt
import de.codeshelf.consoleui.prompt.InputResult
import de.codeshelf.consoleui.prompt.ListResult
import de.codeshelf.consoleui.prompt.builder.ListPromptBuilder
import jline.TerminalFactory
import org.fusesource.jansi.Ansi.ansi
import org.fusesource.jansi.AnsiConsole
import java.awt.Desktop
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
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
        var currentEnvironment = if (currentEnvironmentName == null) {
            null
        } else {
            runCatching { File(baseDir, currentEnvironmentName).takeIf { it.exists() } }.getOrNull()
        }

        if (currentEnvironment == null) {
            println("No active environment detected!")
            currentEnvironment = File(baseDir, selectOrCreateEnvironment(baseDir)).also { it.mkdirs() }
        } else {
            println(ansi().render("Active environment: ").bold().render(currentEnvironment.name).boldOff())
            println()
        }

        val isNewEnvironment = currentEnvironmentName == null
        File(baseDir, "current.txt").writeText(currentEnvironment.name)

        val compose = findCompose()
        val psText = compose.ps(currentEnvironment).executeToText()
        if (psText == null) {
            println("${currentEnvironment.absolutePath} appears to be corrupt. Try deleting this directory!")
            exitProcess(1)
        }

        val psLines =
            psText.lines().filter { !it.trim().startsWith("name", ignoreCase = true) && !it.trim().startsWith("---") }

        Environment(currentEnvironment.name, PortAllocator.Direct)
            .createComposeFile(
                listOf(
                    ComposeService.UCloudBackend,
                    ComposeService.UCloudFrontend,
                    ComposeService.Gateway,
                )
            )

        if (isNewEnvironment || psLines.size <= 1) {
            val shouldStart = confirm(
                prompt,
                "The environment '${currentEnvironment.name}' is not running. Do you want to start it?",
                default = true
            )

            if (!shouldStart) return

            LoadingIndicator("Starting virtual cluster...").use {
                compose.up(currentEnvironment).executeToText()
            }

            LoadingIndicator("Starting UCloud...").use {
                startService(serviceByName("backend"), compose, currentEnvironment).executeToText()
            }

            LoadingIndicator("Waiting for UCloud to be ready...").use {
                val cmd = compose.exec(currentEnvironment, "backend", listOf("curl", "http://localhost:8080"), tty = false)
                cmd.allowFailure = true

                for (i in 0 until 100) {
                    if (cmd.executeToText() != null) break
                    Thread.sleep(1000)
                }
            }
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
                            currentEnvironment,
                            item.name,
                            listOf("tail", "-f", "/tmp/service.log")
                        ).toBashScript()
                    )
                } else {
                    File(postExecPath).writeText(compose.logs(currentEnvironment, item.name).toBashScript())
                }
            }

            TopLevelMenu.openShell -> {
                val item = ServiceMenu(requireExec = true).display(prompt)
                File(postExecPath).writeText(
                    compose.exec(currentEnvironment, item.name, listOf("/bin/bash")).toBashScript()
                )
            }

            TopLevelMenu.createProvider -> {
                val selectedProviders = CreateProviderMenu.display(prompt)
                for (provider in selectedProviders) {
                    run {
                        val indicator = LoadingIndicator("Initializing ${provider.message}...")
                        indicator.display()
                        Thread.sleep(500)
                        indicator.state.set(LoadingState.SUCCESS)
                        indicator.join()
                    }

                    run {
                        val indicator = LoadingIndicator("Registering provider with UCloud/Core...")
                        indicator.display()
                        Thread.sleep(1000)
                        indicator.state.set(LoadingState.SUCCESS)
                        indicator.join()
                    }

                    run {
                        val indicator = LoadingIndicator("Restarting provider...")
                        indicator.display()
                        Thread.sleep(3000)
                        indicator.prompt = "Restart complete!"
                        indicator.state.set(LoadingState.SUCCESS)
                        indicator.join()
                    }
                }
            }

            TopLevelMenu.services -> {
                val service = serviceByName(ServiceMenu().display(prompt).name)

                when (ServiceActionMenu.display(prompt)) {
                    ServiceActionMenu.start -> {
                        File(postExecPath).writeText(
                            startService(service, compose, currentEnvironment).toBashScript()
                        )
                    }

                    ServiceActionMenu.stop -> {
                        if (service.useServiceConvention) {
                            File(postExecPath).writeText(
                                compose.exec(
                                    currentEnvironment,
                                    service.containerName,
                                    listOf("/opt/ucloud/service.sh", "stop")
                                ).toBashScript()
                            )
                        } else {
                            File(postExecPath).writeText(
                                compose.stop(currentEnvironment, service.containerName).toBashScript()
                            )
                        }
                    }

                    ServiceActionMenu.restart -> {
                        if (service.useServiceConvention) {
                            File(postExecPath).writeText(
                                buildString {
                                    appendLine(
                                        compose.exec(
                                            currentEnvironment,
                                            service.containerName,
                                            listOf("/opt/ucloud/service.sh", "stop")
                                        ).toBashScript()
                                    )

                                    appendLine(
                                        compose.exec(
                                            currentEnvironment,
                                            service.containerName,
                                            listOf("/opt/ucloud/service.sh", "start")
                                        ).toBashScript()
                                    )
                                }
                            )
                        } else {
                            File(postExecPath).writeText(
                                buildString {
                                    appendLine(compose.stop(currentEnvironment, service.containerName).toBashScript())
                                    appendLine(compose.start(currentEnvironment, service.containerName).toBashScript())
                                }
                            )
                        }
                    }
                }
            }

            TopLevelMenu.environment -> {
                when (EnvironmentMenu.display(prompt)) {
                    EnvironmentMenu.stop -> {
                        LoadingIndicator("Shutting down virtual cluster...").use {
                            compose.down(currentEnvironment).executeToText()
                        }
                    }

                    EnvironmentMenu.restart -> {
                        LoadingIndicator("Shutting down virtual cluster...").use {
                            compose.down(currentEnvironment).executeToText()
                        }
                        LoadingIndicator("Starting virtual cluster...").use {
                            compose.up(currentEnvironment).executeToText()
                        }
                    }

                    EnvironmentMenu.delete -> {
                        val shouldDelete = confirm(
                            prompt,
                            "Are you sure you want to permanently delete the environment and all the data?"
                        )

                        if (shouldDelete) {
                            LoadingIndicator("Shutting down virtual cluster...").use {
                                compose.down(currentEnvironment).executeToText()
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
                                        "${currentEnvironment.parentFile.absolutePath}:/data",
                                        "alpine:3",
                                        "/bin/sh",
                                        "-c",
                                        "rm -rf /data/${currentEnvironment.name}"
                                    )
                                )
                            }
                        }
                    }

                    EnvironmentMenu.switch -> {
                        LoadingIndicator("Shutting down virtual cluster...").use {
                            compose.down(currentEnvironment).executeToText()
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

private fun startService(
    service: Service,
    compose: DockerCompose,
    currentEnvironment: File,
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
}

object EnvironmentMenu : Menu("Select an action") {
    val stop = item("stop", "Stop environment")
    val restart = item("restart", "Restart environment")
    val delete = item("delete", "Delete environment")
    val switch = item("switch", "Switch environment or create a new one")
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
                println(ansi().cursorUp(if (iteration == 0) 0 else 1).eraseLine().render("[$symbol] $prompt"))

                if (current != LoadingState.IN_PROGRESS) break
                Thread.sleep(50)
                iteration += 1
            }
            println()
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