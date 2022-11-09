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
import java.io.File
import java.util.concurrent.atomic.AtomicReference

fun main(args: Array<String>) {
    try {
        AnsiConsole.systemInstall()
        val version = (runCatching { File("./backend/version.txt").readText() }.getOrNull()
            ?: runCatching { File("../backend/version.txt").readText() }.getOrNull() ?: "").trim()

        println("UCloud $version - Launcher tool")
        println(CharArray(TerminalFactory.get().width) { '-' }.concatToString())

        val prompt = ConsolePrompt()

        val baseDir = File(".compose").also { it.mkdirs() }
        val currentEnvironmentName = runCatching { File(baseDir, "current.txt").readText() }.getOrNull()
        var currentEnvironment = if (currentEnvironmentName == null) {
            null
        } else {
            runCatching { File(baseDir, currentEnvironmentName).takeIf { it.exists() } }.getOrNull()
        }

        if (currentEnvironment == null) {
            println("No active environment detected!")
            val alternativeEnvironments = (baseDir.listFiles() ?: emptyArray()).filter { it.isDirectory }
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
                    currentEnvironment = File(selected.name)
                }
            }
        } else {
            println(ansi().render("Active environment: ").bold().render(currentEnvironment.name).boldOff())
            println()
        }

        if (currentEnvironment == null) {
            val builder = prompt.promptBuilder
            builder
                .createInputPrompt()
                .name("selector")
                .message("Select a name for your environment")
                .defaultValue("default")
                .addPrompt()

            val newEnvironment = (prompt.prompt(builder.build()).values.single() as InputResult).input!!
            currentEnvironment = File(baseDir, newEnvironment).also { it.mkdirs() }
        }

        val isNewEnvironment = currentEnvironmentName == null
        File(baseDir, "current.txt").writeText(currentEnvironment.name)

        val compose = findCompose()
        if (isNewEnvironment || compose.ps(currentEnvironment).executeToText().lines().any { !it.startsWith("NAME") }) {
            val builder = prompt.promptBuilder
            builder
                .createConfirmPromp()
                .name("start")
                .message("The environment '${currentEnvironment.name}' is not running. Do you want to start it?")
                .defaultValue(ConfirmChoice.ConfirmationValue.YES)
                .addPrompt()

            val shouldStart = (prompt.prompt(builder.build()).values.single() as ConfirmResult).confirmed == ConfirmChoice.ConfirmationValue.YES
            if (!shouldStart) return

            LoadingIndicator("Generating virtual cluster...").use {
                Environment(currentEnvironment.name, PortAllocator.Direct)
                    .createComposeFile(
                        listOf(
                            Service.UCloudBackend,
                            Service.UCloudFrontend,
                            Service.Gateway,
                        )
                    )
            }

            LoadingIndicator("Starting virtual cluster...").use {
                compose.up(currentEnvironment).executeToText()
            }
        }

        when (TopLevelMenu.display(prompt)) {
            TopLevelMenu.openLogs -> {
                println("Logs!")
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
        }
        println("Done!")
    } finally {
        TerminalFactory.get().restore()
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
    val openUserInterface = item("ui", "Open user-interface")
    val openShell = item("shell", "Open shell to...")
    val openLogs = item("logs", "Open logs...")
    val createProvider = item("providers", "Create provider...")
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