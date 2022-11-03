package dk.sdu.cloud.cli

import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.plugins.compute.udocker.UDocker

fun UDockerCli(controllerContext: ControllerContext) {
    controllerContext.pluginContext.commandLineInterface?.addHandler(CliHandler("udocker") { args ->
        when (args.getOrNull(0)) {
            "install" -> {
                println(UDocker(controllerContext.pluginContext).install())
            }
        }
    })
}
