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
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.ArrayList
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.min

fun printExplanation(explanation: String) {
    val currentWidth = TerminalFactory.get().width
    val desiredWidth = min(100, currentWidth)
    val paragraphs = ArrayList<String>()

    val currentParagraph = StringBuilder()
    val lines = explanation.trim().split("\n").map { it.trim() }
    for (line in lines) {
        if (line.isEmpty()) {
            paragraphs.add(currentParagraph.toString())
            currentParagraph.clear()
        } else {
            if (currentParagraph.isNotEmpty()) {
                currentParagraph.append(" ")
            }

            currentParagraph.append(line)
        }
    }

    if (currentParagraph.isNotEmpty()) {
        paragraphs.add(currentParagraph.toString())
        currentParagraph.clear()
    }

    print(
        paragraphs.joinToString("\n") {
            it.wrapParagraph(desiredWidth)
        }
    )
}

private fun String.wrapParagraph(columns: Int): String {
    val b = StringBuilder()
    val line = StringBuilder()

    val words = split(" ")
    for (word in words) {
        if (line.isNotEmpty()) {
            line.append(" ")
        }

        if (line.length + word.length > columns) {
            b.appendLine(line.toString().replace("<br> ", "\n").replace("<br>", "\n"))
            line.clear()
        }
        line.append(word)
    }
    if (line.isNotEmpty()) b.appendLine(line.toString().replace("<br> ", "\n").replace("<br>", "\n"))
    return b.toString()
}

data class ListItem(val message: String, val name: String, val isSeparator: Boolean)

fun ListPromptBuilder.add(item: ListItem): ListPromptBuilder {
    if (item.isSeparator) {
        if (item.message == "spacer") {
            return newItem("sep-spacer").text("").add()
        }
        val desiredSize = 80
        val dash = "-".repeat((desiredSize - item.name.length - 2) / 2)
        var text = "$dash ${item.name} $dash"
        while (text.length < desiredSize) text += "-"

        return newItem("sep-$item.name").text(text).add()
    } else {
        return newItem(item.name).text(item.message).add()
    }
}

abstract class Menu(val prompt: String) {
    private val items = ArrayList<ListItem>()

    fun item(name: String, message: String): ListItem {
        val result = ListItem(message, name, false)
        items.add(result)
        return result
    }

    fun separator(message: String): ListItem {
        if (items.isNotEmpty()) {
            items.add(ListItem("spacer", "spacer", true))
        }
        val result = ListItem(message, message, true)
        items.add(result)
        return result
    }

    fun display(prompt: ConsolePrompt): ListItem {
        val builder = prompt.promptBuilder
        val b = builder.createListPrompt().message(this.prompt)
        b.pageSize(40)
        for (item in items) {
            b.add(item)
        }
        b.addPrompt()
        val selectedId = (prompt.prompt(builder.build()).values.single() as ListResult).selectedId
        return items.find { it.name == selectedId } ?: error("Unknown selection")
    }
}

class TopLevelMenu : Menu("Select an item from the menu") {
    val remoteDevelopmentSep = if (environmentIsRemote) separator("Remote development") else null
    val portforward = if (environmentIsRemote) item("port-forward", "Enable port-forwarding (REQUIRED)") else null

    val managementSep = separator("Management")
    val createProvider = item("providers", buildString {
        append("Create provider...")
        if (listConfiguredProviders().isEmpty()) {
            append(" (Recommended)")
        }
    })
    val services = item("services", "Manage services...")
    val environment = item("environment", "Manage environment...")

    val developmentSep = separator("Development")
    val openUserInterface = item("ui", "Open user-interface...")
    val openShell = item("shell", "Open shell to...")
    val openLogs = item("logs", "Open logs...")
    val test = item("test", "Run a test suite...")

    val supportSep = separator("Support")
    val help = item("help", "Get help with UCloud")
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
    init {
        val configuredProviders = listConfiguredProviders()
        val allProviders = ComposeService.allProviders()

        for (provider in allProviders) {
            val isConfigured = provider.name in configuredProviders
            item(
                provider.name,
                provider.title,
                default = isConfigured,
                disabled = isConfigured,
                disabledText = "Already configured"
            )
        }
    }
}

enum class LoadingState {
    IN_PROGRESS,
    SUCCESS,
    FAILURE,
    INFO,
    WARNING
}

const val numberOfStatusLines = 10
val messageQueueLock = Any()
val messageQueue = ArrayList<String>()
fun printStatus(message: String) {
    synchronized(messageQueueLock) {
        messageQueue.add(message)
    }
}

class LoadingIndicator(var prompt: String) {
    private lateinit var thread: Thread
    val state = AtomicReference(LoadingState.IN_PROGRESS)
    val spinnerFrames = listOf(
        " ⣾ ", " ⣽ ", " ⣻ ", " ⢿ ", " ⡿ ", " ⣟ ", " ⣯ ", " ⣷ ",
        " ⠁ ", " ⠂ ", " ⠄ ", " ⡀ ", " ⢀ ", " ⠠ ", " ⠐ ", " ⠈ "
    )

    @OptIn(ExperimentalContracts::class)
    inline fun use(code: () -> Unit) {
        contract {
            callsInPlace(code, InvocationKind.EXACTLY_ONCE)
        }

        var success = true
        try {
            display()
            code()
        } catch (ex: Throwable) {
            success = false
            state.set(LoadingState.FAILURE)
            throw ex
        } finally {
            if (success) state.set(LoadingState.SUCCESS)
            join()
        }
    }

    fun display() {
        if (isHeadless) {
            thread = Thread {
                println(prompt)

                while (true) {
                    val current = state.get()

                    val tail = synchronized(messageQueueLock) {
                        ArrayList(messageQueue).also {
                            messageQueue.clear()
                        }
                    }

                    for (message in tail) {
                        println(message)
                    }

                    if (current != LoadingState.IN_PROGRESS) break

                    Thread.sleep(50)
                }
            }.also { it.start() }
            return
        }
        val maxLineLength = TerminalFactory.get().width

        thread = Thread {
            var iteration = 0

            val statusLines = Array(numberOfStatusLines) { "" }
            var activeLines = 0
            var lastActiveLines = 0

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

                val tail = synchronized(messageQueueLock) {
                    val tail = messageQueue.takeLast(numberOfStatusLines)
                    messageQueue.clear()
                    ArrayDeque(tail.toMutableList())
                }

                if (tail.isNotEmpty()) {
                    if (activeLines < numberOfStatusLines) {
                        // Fill the initial buffer, if possible
                        while (activeLines < numberOfStatusLines && tail.isNotEmpty()) {
                            statusLines[activeLines] = tail.removeFirst()
                            activeLines++
                        }
                    }

                    if (activeLines >= numberOfStatusLines) {
                        val tailList = tail.toList()
                        if (tail.size < numberOfStatusLines) {
                            val shift = tail.size
                            for (i in statusLines.indices) {
                                if (i + shift >= statusLines.size) break
                                statusLines[i] = statusLines[i + shift]
                            }
                        }

                        val startIdx = numberOfStatusLines - tail.size
                        for (i in startIdx until numberOfStatusLines) {
                            statusLines[i] = tailList[i - startIdx]
                        }
                    }
                }

                val message = "[$symbol] $prompt"
                if (iteration == 0) {
                    println(ansi().bold().render(message).boldOff())
                } else {
                    println(ansi().cursorUp(lastActiveLines + 1).eraseLine().bold().render(message).boldOff())
                }
                for (i in 0 until activeLines) {
                    val line = if (statusLines[i].length > maxLineLength) {
                        statusLines[i].substring(0, maxLineLength)
                    } else {
                        statusLines[i]
                    }
                    println(ansi().eraseLine().render(line))
                }

                if (current != LoadingState.IN_PROGRESS) break
                Thread.sleep(50)
                iteration += 1
                lastActiveLines = activeLines
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

fun queryText(prompt: ConsolePrompt, question: String, defaultValue: String? = null, mask: Boolean = false): String {
    while (true) {
        val builder = prompt.promptBuilder
        builder
            .createInputPrompt()
            .name("question")
            .message(question)
            .also { if (defaultValue != null) it.defaultValue(defaultValue) }
            .also { if (mask) it.mask('*') }
            .addPrompt()

        try {
            return (prompt.prompt(builder.build()).values.single() as InputResult).input!!
        } catch (ignored: NullPointerException) {

        }
    }
}
