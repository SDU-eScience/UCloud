package dk.sdu.cloud

import dk.sdu.cloud.utils.*

fun runInstaller() {
    sendTerminalMessage {
        bold { red { line("No configuration detected!") } }
        line()
        line("Development setup not detected - Manual configuration is required!")
        line("See documentation for more details.")
    }
}
