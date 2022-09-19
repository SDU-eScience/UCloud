package dk.sdu.cloud.cli

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.utils.TerminalMessageDsl
import dk.sdu.cloud.utils.sendTerminalMessage
import kotlin.system.exitProcess

class CliHandler(val plugin: String, val handler: suspend CommandLineInterface.(args: List<String>) -> Unit)

class CommandLineInterface(val isParsable: Boolean, private val args: List<String>) {
    private val handlers = ArrayList<CliHandler>()

    fun addHandler(cliHandler: CliHandler) {
        handlers.add(cliHandler)
    }

    suspend fun execute(plugin: String): Nothing {
        try {
            for (handler in handlers) {
                if (handler.plugin.equals(plugin, ignoreCase = true)) {
                    with(handler) {
                        handler(args)
                    }
                    exitProcess(0)
                }
            }
        } catch (ex: RPCException) {
            println(ex.why)
            exitProcess(ex.httpStatusCode.value)
        } catch (ex: Throwable) {
            ex.printStackTrace()
            exitProcess(1)
        }

        println("Unknown plugin: $plugin")
        exitProcess(1)
    }
}

class CommandLineUsageDsl(var command: String, var title: String) {
    var description: String? = null

    private val subcommands = ArrayList<CommandLineSubCommand>()

    fun subcommand(subcommand: String, description: String, builder: CommandLineSubCommand.() -> Unit = {}) {
        subcommands.add(CommandLineSubCommand(subcommand, description).also(builder))
    }

    fun send() {
        sendTerminalMessage {
            bold { inline("Usage: ") }
            code {
                inline("ucloud ")
                inline(command)
                inline(" [subcommand] [...args]")
            }

            inline(" - ")
            line(title)

            val description = description
            if (description != null) line(description)

            line()

            bold { line("Subcommands:") }

            for (command in subcommands) {
                with(command) { send() }
            }
        }

    }
}

class CommandLineSubCommand(val name: String, val description: String) {
    private val args = ArrayList<CommandLineArgumentDsl>()

    fun arg(name: String, optional: Boolean = false, description: String? = null, builder: CommandLineArgumentDsl.() -> Unit = {}) {
        args.add(CommandLineArgumentDsl(name, optional, description).also(builder))
    }

    fun TerminalMessageDsl.send() {
        inline("  ")
        code {
            bold { inline(name) }
            for (arg in args) {
                inline(" ")
                if (arg.optional) inline("[")
                else inline("<")
                inline(arg.name)
                if (arg.optional) inline("]")
                else inline(">")
            }
        }
        inline(" - ")
        inline(description)
        line()

        for (arg in args) {
            with(arg) { send() }
        }
    }
}

class CommandLineArgumentDsl(val name: String, val optional: Boolean, var description: String? = null) {
    fun TerminalMessageDsl.send() {
        inline("    ")
        inline(name)

        val description = description
        if (description != null) {
            inline(": ")
            inline(description)
            line()
        }
    }
}

fun sendCommandLineUsage(command: String, title: String, builder: CommandLineUsageDsl.() -> Unit): Nothing {
    sendCommandLineUsageNoExit(command, title, builder)
    exitProcess(0)
}

fun sendCommandLineUsageNoExit(command: String, title: String, builder: CommandLineUsageDsl.() -> Unit) {
    CommandLineUsageDsl(command, title).also(builder).send()
}

suspend fun genericCommandLineHandler(block: suspend () -> Unit): Nothing {
    try {
        block()
        exitProcess(0)
    } catch (ex: Throwable) {
        sendTerminalMessage {
            bold { red { line("Error!") } }
            line()
            bold { line(ex.message ?: "Unknown error") }
        }

        exitProcess(1)
    }
}
