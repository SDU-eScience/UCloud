package launcher

import (
	"fmt"
	"os"
	"slices"
	"strings"
	"ucloud.dk/launcher/pkg/termio"
)

func PrintHelp() {
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
	os.Exit(0)
}

func cliIntercept(args []string) {
	cmd := args[0]
	if cmd == "" {
		return
	}
	commands := Commands{}
	switch cmd {
	case "svc", "service":
		{
			InitializeServiceList()
			if len(args) < 2 {
				PrintHelp()
			}
			svcName := args[1]
			if svcName == "" {
				PrintHelp()
			}
			service := serviceByName(svcName)
			if service == nil {
				fmt.Println("Unknown service:", svcName, "! Try on of the following:")
				for _, ser := range AllServices {
					fmt.Println("  - ", ser.containerName, ": ", ser.title)
				}
				os.Exit(0)
			}

			if len(args) < 3 {
				PrintHelp()
			}
			svcCommand := args[2]
			if svcCommand == "" {
				PrintHelp()
			}
			switch svcCommand {
			case "sync":
				{
					SyncRepository()
				}
			case "start", "stop", "restart":
				{
					GenerateComposeFile(true)
					SyncRepository()
					switch svcCommand {
					case "start":
						{
							commands.serviceStart(svcName)
						}
					case "stop":
						{
							commands.serviceStop(svcName)
						}
					case "restart":
						{
							commands.serviceStop(svcName)
							commands.serviceStart(svcName)
						}
					}
					if slices.Contains(args, "--follow") {
						commands.openLogs(svcName)
					}
				}
			case "sh", "shell", "exec":
				{
					commands.openShell(svcName)
				}
			case "logs":
				{
					commands.openLogs(svcName)
				}

			default:
				{
					PrintHelp()
				}

			}

		}
	case "addon", "addons":
		{
			InitializeServiceList()
			if len(args) < 2 {
				PrintHelp()
			}
			providerName := args[1]
			if providerName == "" {
				PrintHelp()
			}

			provider := NewProvider() //TODO()
			if provider == nil {
				fmt.Println("Unknown provider:", providerName, "! Try on of the following:")
				for _, pro := range AllProviders {
					fmt.Println("  - ", pro.name, ": ", pro.title)
				}
				os.Exit(0)
			}

			if len(args) < 3 {
				PrintHelp()
			}
			addonName := args[2]
			if addonName == "" {
				PrintHelp()
			}

			if provider.addons[addonName] == "" {
				fmt.Println("Unknown addon:", addonName, "! Try on of the following:")
				for _, addon := range provider.addons {
					fmt.Println(" - ", addon)
				}
				os.Exit(0)
			}

			AddAddon(providerName, addonName)
			GenerateComposeFile(true)
			SyncRepository()

			err := termio.LoadingIndicator("starting addon containers", func(output *os.File) error {
				compose.Up(currentEnvironment, true).executeToText()
				return nil
			})
			SoftCheck(err)

			err = termio.LoadingIndicator(strings.Join([]string{"Installing addon ", providerName, "/", addonName}, ""), func(output *os.File) error {
				if provider.name == "go-slurm" {
					goSlurm.installAddon(addonName)
					goSlurm.startAddon(addonName)
				}
				return nil
			})
			SoftCheck(err)
		}
	case "env", "environment":
		{
			if len(args) < 2 {
				PrintHelp()
			}
			envCommand := args[1]
			if envCommand == "" {
				PrintHelp()
			}

			GenerateComposeFile(true)
			SyncRepository()

			switch envCommand {
			case "status":
				{
					commands.environmentStatus()
				}
			case "stop":
				{
					commands.environmentStop()
				}
			case "delete":
				{
					commands.environmentDelete(true)
				}
			case "restart":
				{
					commands.environmentRestart()
				}
			}
		}
	case "port-forward":
		{
			commands.portForward()
		}
	case "import-apps":
		{
			commands.importApps()
		}
	case "add-provider":
		{
			if len(args) < 2 {
				PrintHelp()
			}
			provider := args[1]
			if provider == "" {
				PrintHelp()
			}
			commands.createProvider(provider)
		}
	case "snapshot":
		{
			if len(args) < 2 {
				PrintHelp()
			}
			snapshotName := args[1]
			if snapshotName == "" {
				PrintHelp()
			}
			commands.createSnapshot(snapshotName)
		}
	case "restore":
		{
			if len(args) < 2 {
				PrintHelp()
			}
			snapshotName := args[1]
			if snapshotName == "" {
				PrintHelp()
			}
			commands.restoreSnapshot(snapshotName)
		}
	}
}
