package dk.sdu.cloud

import dk.sdu.cloud.utils.*
import libc.clib

fun runInstaller() {
    sendTerminalMessage {
        bold { red { line("No configuration detected!") } }
        line()
        bold { red { line("Are the configuration files readable by the user with uid ${clib.getuid()}?") } }
        line("If there is no configuration yet, then please see the documentation for how to configure the integration module.")
    }
}
