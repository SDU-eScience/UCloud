package dk.sdu.cloud.cli

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.controllers.ControllerContext
import kotlin.system.exitProcess

class CliHandler(val plugin: String, val handler: suspend (args: List<String>) -> Unit)

class CommandLineInterface(private val args: List<String>) {
    private val handlers = ArrayList<CliHandler>()

    fun addHandler(cliHandler: CliHandler) {
        handlers.add(cliHandler)
    }

    suspend fun execute(plugin: String): Nothing {
        try {
            for (handler in handlers) {
                if (handler.plugin.equals(plugin, ignoreCase = true)) {
                    handler.handler(args)
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

fun registerAlwaysOnCommandLines(controllerContext: ControllerContext) {
    ConnectionCli(controllerContext)
    ProductsCli(controllerContext)
}
