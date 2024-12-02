package launcher

import (
	"encoding/xml"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"ucloud.dk/launcher/pkg/termio"
)

var postExecFile os.File

type Commands struct {
}

func (c Commands) portForward() {
	InitializeServiceList()
	ports := Remapped(portAllocator).allocatedPorts
	conn := RemoteExecutableCommandFactory(commandFactory).connection

	forward := ""

	for k, port := range ports {
		forward = forward + " -L " + strconv.Itoa(k) + ":localhost:" + strconv.Itoa(port)
	}

}
func (c Commands) openUserInterface(serviceName string) {
	service := serviceByName(serviceName)
	address := service.address
	uiHelp := service.uiHelp
	if !Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE) {
		fmt.Println("Unable to open web-browser. Open this URL in your own browser:")
		fmt.Println(address)
	} else {
		Desktop.getDesktop().browse(URI(address))
	}

	if uiHelp != "" {
		printExplantion(uiHelp)
	}

}
func (c Commands) openLogs(serviceName string) {
	service := serviceByName(serviceName)
	if service.useServiceConvention {
		postExecFile.appendTest //TODO
	} else {
		postExecFile.appendText //TODO
	}
}

func (c Commands) openShell(serviceName string) {
	postExecFile.appendTxt //TODO
}

func (c Commands) createProvider(providerName string) {
	resolvedProvider := ProviderFromName(providerName)
	startProvdierService(providerName)

	credentials := ProviderCredentials{}
	termio.LoadingIndicator("Registering provider with UCloud/Core", func(output *os.File) error {
		credentials = registerProvider(providerName, providerName, 8889)
		return nil
	})

	termio.LoadingIndicator("Configuring provider...", func(output *os.File) error {
		resolvedProvider.Install(credentials)
		return nil
	})

	termio.LoadingIndicator("Starting provider...", func(output *os.File) error {
		compose.Up(currentEnvironment, true).ExecuteToText()
		return nil
	})

	if resolvedProvider.CanRegisterProducts() {
		termio.LoadingIndicator("Registering proudct with UCloud/Core", func(output *os.File) error {
			compose.Exec(
				currentEnvironment,
				providerName,
				[]string{
					"sh", "-c", `
						while ! test -e "/var/run/ucloud/ucloud.sock"; do
							sleep 1
							echo "Waiting for UCloud/IM to be ready..."
						done
					`,
				},
				false,
			)

			compose.Exec(
				currentEnvironment,
				providerName,
				[]string{"sh", "-c", "yes | ucloud products register"},
				false,
			)
			//TODO Deadline
			return nil
		})

		termio.LoadingIndicator("Restarting provider...", func(output *os.File) error {
			stopService(serviceByName(providerName)).executeToText()
			startService(serviceByName(providerName)).executeToText()
			return nil
		})

		termio.LoadingIndicator("Granting credits to provider project", func(output *os.File) error {
			accessToken := fetchAssecToken()
			productPage := //TODO()
			productItems := productPage["items"]

			//TODO
		})

	}
}
func (c Commands) serviceStart(serviceName string) {
	postExecFile.appentText(//TODO)
}

func (c Commands) serviceStop(serviceName string)  {
	postExecFile.appendText(stopService(serviceByName(serviceName)).ToBashScript())
}
func (c Commands) environmentStop() {
	termio.LoadingIndicator("Shutting down virtual cluster...", func(output *os.File) error {
		compose.Down(currentEnvironment).streamOutput().executeToText()
		return nil
	})
}
func (c Commands) environmentStart() {
	StartCluster(compose, false)
}

func (c Commands) environmentRestart() {
	c.environmentStop()
	c.environmentStart()
}

func (c Commands) environmentDelete(shutdown bool) {
	if shutdown {
		termio.LoadingIndicator("Shutting down virtual cluster...", func(output *os.File) error {
			compose.Down(currentEnvironment, true).StreamOutput().ExecuteToText()
			v, ok := compose.(Plugin)
			if ok {
				for _, name := range allVolumeNames {
					args := []string{
						FindDocker(),
						"volume",
						"rm",
						name,
						"-f",
					}
					ExecutableCommand{
						args:       args,
						workingDir: nil,
						fn: func(text ProcessResultText) string {
							return text
						},
						allowFailure:     false,
						deadlineInMillis: 1000 * 60 * 5,
						streamOutput:     false,
					}.streamOutput().ExecuteToText()
				}
			}
			return nil
		})

		termio.LoadingIndicator("Deleting files associated with cirtual cluster...", func(output *os.File) error {
			path := filepath.Dir(currentEnvironment.GetAbsolutePath())
			ex := ExecutableCommand{
				args:       []string{FindDocker(), "run", "-v", path+":/data", "alpine:3", "/bin/sh", "-c", "rm -rf /data/"+currentEnvironment.Name()},
				workingDir: nil,
				fn: func(text ProcessResultText) string {
					return text
				},
				allowFailure: false,
				deadlineInMillis: 1000 * 60 * 5,
				streamOutput:     false,
			}

			if shutdown {
				ex.setAllowFailure()
			}

			ex.ExecuteToText()
			return nil
		})
		pa, err := filepath.Abs(localEnvironment.Name())
		HardCheck(err)
		parent := filepath.Dir(pa)
		err = os.Remove(parent+"current.txt")
		HardCheck(err)
		os.RemoveAll(pa)
	}
}
func (c Commands) environmentStatus() {
	postExecFile.AppendText() //TODO
}
func (c Commands) importApps() {
	termio.LoadingIndicator("Importing applications", func(output *os.File) error {
		checksum := "ea9ab32f52379756df5f5cbbcefb33928c49ef8e2c6b135a5048a459e40bc6b2"
		v, ok = callService(
			"backend",
			"POST",
			"http://localhost:8080/api/hpc/apps/devImport",
			fetchAccessToken(),
			`
				{
					"endpoint": "https://launcher-assets.cloud.sdu.dk/$checksum.zip",
					"checksum": "$checksum"
				}
			`,
		)

		if (!ok) {
			panic("Failed to import applications (see backend logs)." +
				" You might need to do a git pull to get the latest version.")
		}
		return nil
	})
}

func (c Commands) createSnapshot(snapshotName string) {
	InitializeServiceList()

	termio.LoadingIndicator("Creating snapshot...", func(output *os.File) error {
		for _, service := range AllServices {
			if service.useServiceConvention {
				compose.Exec(
					currentEnvironment,
					service.containerName,
					[]string {"/opt/ucloud/service.sh", "snapshot", snapshotName},
					false,
				).allowFailure().streamOutput().executeToText()
			}
		}
		return nil
	})
}

func (c Commands) restoreSnapshot(snapshotName string) {
	InitializeServiceList()

	termio.LoadingIndicator("Restorting snapshot...", func(output *os.File) error {
		for _, service := range AllServices {
			if service.useServiceConvention {
				compose.Exec(
					currentEnvironment,
					service.containerName,
					[]string {"/opt/ucloud/service.sh", "restore", snapshotName},
					false,
				).allowFailure().streamOutput().executeToText()
			}
		}
		return nil
	})
}
