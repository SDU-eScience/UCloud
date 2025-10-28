package launcher

import (
	"encoding/json"
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
	println("- write-certs <path>: Write certificates to path given")
	println("- install-certs: Install certificates to the local machine")
	os.Exit(0)
}

func CliIntercept(args []string) {
	cmd := args[0]
	if cmd == "" {
		return
	}

	switch cmd {
	case "svc", "service":
		InitializeServiceList()
		if len(args) < 2 {
			PrintHelp()
		}
		svcName := args[1]
		if svcName == "" {
			PrintHelp()
		}
		service := ServiceByName(svcName)
		if service.containerName == "" || service.title == "" {
			fmt.Println("Unknown service:", svcName, "! Try one of the following:")
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
		case "start", "stop", "restart":
			GenerateComposeFile(true)
			switch svcCommand {
			case "start":
				ServiceStart(svcName)

			case "stop":
				ServiceStop(svcName)

			case "restart":
				ServiceStop(svcName)
				ServiceStart(svcName)
			}

			if slices.Contains(args, "--follow") {
				OpenLogs(svcName)
			}

		case "sh", "shell", "exec":
			OpenShell(svcName)

		case "logs":
			OpenLogs(svcName)

		default:
			PrintHelp()

		}

	case "addon", "addons":
		InitializeServiceList()
		if len(args) < 2 {
			PrintHelp()
		}
		providerName := args[1]
		if providerName == "" {
			PrintHelp()
		}

		provider := ProviderFromName(providerName)
		if provider == nil {
			fmt.Println("Unknown provider:", providerName, "! Try on of the following:")
			for _, pro := range AllProviders {
				fmt.Println("  - ", pro.Name(), ": ", pro.Title())
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

		if provider.Addons()[addonName] == "" {
			fmt.Println("Unknown addon:", addonName, "! Try on of the following:")
			for _, addon := range provider.Addons() {
				fmt.Println(" - ", addon)
			}
			os.Exit(0)
		}

		AddAddon(providerName, addonName)
		GenerateComposeFile(true)

		err := termio.LoadingIndicator("starting addon containers", func() error {
			compose.Up(currentEnvironment, true).ExecuteToText()
			return nil
		})
		SoftCheck(err)

		err = termio.LoadingIndicator(strings.Join([]string{"Installing addon ", providerName, "/", addonName}, ""), func() error {
			if provider.Name() == "go-slurm" {
				goSlurm.InstallAddon(addonName)
				goSlurm.StartAddon(addonName)
			}
			return nil
		})
		SoftCheck(err)

	case "env", "environment":
		if len(args) < 2 {
			PrintHelp()
		}
		envCommand := args[1]
		if envCommand == "" {
			PrintHelp()
		}

		GenerateComposeFile(true)

		switch envCommand {
		case "status":
			EnvironmentStatus()
		case "stop":
			EnvironmentStop()
		case "delete":
			EnvironmentDelete(true)
		case "restart":
			EnvironmentRestart()
		}

	case "import-apps":
		ImportApps()

	case "install-certs":
		InstallCerts()

	case "write-certs":
		if len(args) < 1 {
			PrintHelp()
		} else {
			path := args[1]
			WriteCerts(path)
		}

	case "add-provider":
		if len(args) < 2 {
			PrintHelp()
		}
		provider := args[1]
		if provider == "" {
			PrintHelp()
		}
		CreateProvider(provider)

	case "snapshot":
		if len(args) < 2 {
			PrintHelp()
		}
		snapshotName := args[1]
		if snapshotName == "" {
			PrintHelp()
		}
		CreateSnapshot(snapshotName)

	case "restore":
		if len(args) < 2 {
			PrintHelp()
		}
		snapshotName := args[1]
		if snapshotName == "" {
			PrintHelp()
		}
		RestoreSnapshot(snapshotName)

	case "test-e2e":
		testCommand := testCommandFromArgs(args)

		// Create one user
		withResourceUsername := "user-resources"
		withResourcePassword := "user-resources-password"
		locationOrigin := "https://ucloud.localhost.direct/"
		createUser(withResourceUsername, withResourcePassword, "USER")

		pathToTestInfo := repoRoot.GetAbsolutePath() + "/frontend-web/webclient/tests/test_data.json"
		err := os.WriteFile(pathToTestInfo, []byte(`{
		    "location_origin": "`+locationOrigin+`",
		    "users": {
		        "with_resources": {
		            "username": "`+withResourceUsername+`",
		            "password": "`+withResourcePassword+`"
		        },
		        "without_resources": {

		        }
		    }
		}`), 0777)
		if err != nil {
			panic(err)
		}

		// CallService("backend") // trying to grant resources. No idea if this is the actual issue.

		runCommand([]string{"npx", "playwright", "install"})
		runCommand([]string{"npx", "playwright", "install-deps"})
		runCommand(testCommand)

		// TODO: Create users, one with resources, and one without.
	}

	os.Exit(0)
}
func createUser(username string, password string, role string) {
	accessToken := FetchAccessToken()
	mail := username + "@fake-mail.com"
	response := CallService(
		"backend",
		"POST",
		"http://localhost:8080/auth/users/register",
		accessToken,
		//language=json
		`[{firstnames: `+username+`, lastname: `+username+`, username: `+username+`, password: `+password+`,  role: `+role+`, email: `+mail+`}]`,
		[]string{},
	)
	fmt.Println(response)
	// TODO(Jonas): better error handling. See below how responses look

	var errorMessage struct {
		Why string `json:"why"`
	}

	_ = json.Unmarshal([]byte(response), &errorMessage)

	if errorMessage.Why != "" {
		if strings.Contains(errorMessage.Why, "Conflict") {
			fmt.Println("User already exists. Proceeding.")
		} else {
			panic("Unhandled error: " + errorMessage.Why + ". Bailing")
		}
	} else {
		fmt.Println("User " + username + " created")
	}
}

func runCommand(args []string) {
	fmt.Println(strings.Join(args, " "))
	cmd := NewLocalExecutableCommand(args, LocalFile{path: "../frontend-web/webclient/"}, PostProcessorFunc)
	cmd.SetStreamOutput()
	result := cmd.ExecuteToText()
	if result.First != "" {
		fmt.Println(result.First)
	}
}

func testCommandFromArgs(args []string) []string {
	cmdToRun := []string{"npx", "playwright"}
	if len(args) > 1 {
		arg := args[1]

		switch arg {
		case "ui":
			cmdToRun = append(cmdToRun, "test", "--ui")
		case "headed":
			cmdToRun = append(cmdToRun, "test", "--headed")
		case "report":
			cmdToRun = append(cmdToRun, "show-report")
		default:
			panic("Unknown argument for testing: '" + arg + "'")
		}
	} else {
		// Default that should run on CI
		cmdToRun = append(cmdToRun, "test")
	}
	return cmdToRun
}

/*
	./launcher test-e2e # creating user successfully
	[✅] Connecting to compose environment
	npx playwright test
	[{"accessToken":"FOOBAR","refreshToken":"BAZ","csrfToken":"QUX"}]

	./launcher test-e2e # already exists
	[✅] Connecting to compose environment
	npx playwright test
	{"why":"Conflict","errorCode":null}
*/
