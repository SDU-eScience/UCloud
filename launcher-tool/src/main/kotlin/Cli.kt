package dk.sdu.cloud

import kotlin.system.exitProcess

fun printHelp(): Nothing {
    println("Usage: ./launcher <commands> [args] [opts]")
    println("NOTE: Not all functionality is available through the non-interactive CLI. Use the interactive CLI for other purposes (./launcher).")

    println("Commands:")
    println()
    println("- svc [serviceName] <command> [opts]")
    println("  Service sub-commands:")
    println("  - sync: Synchronize data from your local machine to the remote environment")
    println("  - start: Synchronizes data and starts the service, if not already running")
    println("  - stop: Synchronizes data and stop the service, if not already stopped")
    println("  - restart: Synchronizes data and restarts the service")
    println("  - sh: Open a shell to the service")
    println("  - logs: Opens the logs to a service")
    println("  Service options:")
    println("  - --follow: Follow the logs of the service after performing the normal command")
    println()
    println("- env [command] [opts]")
    println("  Environment sub-commands:")
    println("  - status: View the status of the environment")
    println("  - stop: Stop the environment")
    println("  - restart: Restart the environment")
    println("  - delete: Delete the environment permanently")
    println()
    println("- port-forward: Initializes port forwarding for remote environments")
    println("- import-apps: Import test applications")
    exitProcess(0)
}

fun cliIntercept(args: List<String>) {
    val cmd = args.getOrNull(0) ?: return
    when (cmd) {
        "svc", "service" -> {
            initializeServiceList()
            val svcName = args.getOrNull(1) ?: printHelp()
            runCatching { serviceByName(svcName) }.getOrNull() ?: run {
                println("Unknown service: $svcName! Try one of the following:")
                allServices.forEach {
                    println("  - ${it.containerName}: ${it.title}")
                }
                exitProcess(0)
            }

            val svcCommand = args.getOrNull(2) ?: printHelp()
            when (svcCommand) {
                "sync" -> {
                    syncRepository()
                }

                "start", "stop", "restart" -> {
                    generateComposeFile()
                    syncRepository()
                    when (svcCommand) {
                        "start" -> Commands.serviceStart(svcName)
                        "stop" -> Commands.serviceStop(svcName)
                        "restart" -> {
                            Commands.serviceStop(svcName)
                            Commands.serviceStart(svcName)
                        }
                    }

                    if (args.contains("--follow")) {
                        Commands.openLogs(svcName)
                    }
                }

                "sh", "shell", "exec" -> {
                    Commands.openShell(svcName)
                }

                "logs" -> {
                    Commands.openLogs(svcName)
                }

                else -> printHelp()
            }
        }

        "addon", "addons" -> {
            initializeServiceList()
            val svcName = args.getOrNull(1) ?: printHelp()
            val provider = runCatching { ComposeService.providerFromName(svcName) }.getOrNull() ?: run {
                println("Unknown service: $svcName! Try one of the following:")
                ComposeService.allProviders().forEach {
                    println("  - ${it.name}: ${it.title}")
                }
                exitProcess(0)
            }

            val addonName = args.getOrNull(2) ?: printHelp()
            if (addonName !in provider.addons()) {
                println("Unknown addon: $addonName! Try one of the following:")
                for (addon in provider.addons().sorted()) {
                    println(" - $addon")
                }
                exitProcess(0)
            }

            addAddon(svcName, addonName)
            generateComposeFile()
            syncRepository()

            LoadingIndicator("Starting addon containers").use {
                compose.up(currentEnvironment, noRecreate = true).executeToText()
            }

            LoadingIndicator("Installing addon $svcName/$addonName").use {
                provider.installAddon(addonName)
                provider.startAddon(addonName)
            }
        }

        "env", "environment" -> {
            val envCommand = args.getOrNull(1) ?: printHelp()

            generateComposeFile()
            syncRepository()

            when (envCommand) {
                "status" -> {
                    Commands.environmentStatus()
                }

                "stop" -> {
                    Commands.environmentStop()
                }

                "delete" -> {
                    Commands.environmentDelete()
                }

                "restart" -> {
                    Commands.environmentRestart()
                }
            }
        }

        "port-forward" -> {
            Commands.portForward()
        }

        "install-certs" -> {
            Commands.installCertificates()
        }

        "write-certs" -> {
            Commands.writeCertificates(args.getOrNull(1) ?: printHelp())
        }

        "import-apps" -> {
            Commands.importApps()
        }

        "add-provider" -> {
            val provider = args.getOrNull(1) ?: printHelp()
            Commands.createProvider(provider)
        }

        "snapshot" -> {
            val snapshotName = args.getOrNull(1) ?: printHelp()
            Commands.createSnapshot(snapshotName)
        }

        "restore" -> {
            val snapshotName = args.getOrNull(1) ?: printHelp()
            Commands.restoreSnapshot(snapshotName)
        }
    }
    exitProcess(0)
}
