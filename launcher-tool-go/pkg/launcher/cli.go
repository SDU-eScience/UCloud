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
		default:
			fmt.Printf("Unrecognized command '%s'. Exiting.\n", envCommand)
			os.Exit(0)
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

	case "test":
		RunTests(args)
	default:
		fmt.Printf("Unknown command '%s'\n", cmd)
	}
	os.Exit(0)
}

func RunTests(args []string) {
	// Create one user
	withResourceUsername := "user-resources"
	withResourcePassword := "user-resources-password"
	locationOrigin := "https://ucloud.localhost.direct/"

	newUserWithResources := createUser(withResourceUsername, withResourcePassword, "USER")
	if newUserWithResources != nil {
		newUserWithResources.SetupUserWithResources()
	}

	withNoResourcesUsername := "user-no-resources"
	withNoResourcesPassword := "user-no-resources-password"
	createUser(withNoResourcesUsername, withNoResourcesPassword, "USER")

	pathToTestInfo := repoRoot.GetAbsolutePath() + "/frontend-web/webclient/tests/test_data.json"
	if err := os.WriteFile(pathToTestInfo, fmt.Appendf(nil, `{
		"location_origin": "%s",
		"users": {
			"with_resources": {
				"username": "%s",
				"password": "%s"
			},
			"without_resources": {
				"username": "%s",
				"password": "%s"
			}
		}
	}`, locationOrigin, withResourceUsername, withResourcePassword, withNoResourcesUsername, withNoResourcesPassword), 0777); err != nil {
		panic(err)
	}

	startPlaywright(args)
}

func startPlaywright(args []string) {
	runCommand([]string{"npx", "playwright", "install"})
	runCommand([]string{"npx", "playwright", "install-deps"})
	runCommand(testCommandFromArgs(args))
}

func createResourcesAndGifts() int {
	createRootAllocation()
	return createGift()
}

func (u *UserTokens) SetupUserWithResources() {
	if u != nil {
		// User didn't exist, so create root allocation, gift and have user claim the gift.
		giftId := createResourcesAndGifts()
		claimGifts(u.AccessToken, giftId)
		// Connect to provider
		CallService("backend", "POST", "http://localhost:8080/api/providers/integration/connect", u.AccessToken, fmt.Sprintf(`{provider: "%s"}`, getProviderId()), []string{})
	}
}

func createGift() int {
	result := CallService("backend", "POST", "http://localhost:8080/api/gifts", FetchAccessToken(true), fmt.Sprintf(`{
		"id": 0,
		"criteria": [{"type": "anyone"}],
		"description": "Testing purposes",
		"resources": [
			{
				"balanceRequested": 1000,
				"category": "public-ip",
				"grantGiver": "",
				"period": {
					"end": 0,
					"start": 0
				},
				"provider": "gok8s"
			},
			{
				"balanceRequested": 1000,
				"category": "storage",
				"grantGiver": "",
				"period": {
					"end": 0,
					"start": 0
				},
				"provider": "gok8s"
			},
			{
				"balanceRequested": 60000,
				"category": "u1-standard",
				"grantGiver": "",
				"period": {
					"end": 0,
					"start": 0
				},
				"provider": "gok8s"
			},
			{
				"balanceRequested": 60000,
				"category": "u2-standard",
				"grantGiver": "",
				"period": {
					"end": 0,
					"start": 0
				},
				"provider": "gok8s"
			}
		],
		"resourcesOwnedBy": "%s",
		"title": "Gift for testing accounts",
		"renewEvery": 0
	}`, getRootProjectId()), []string{"-H", "Project: " + getRootProjectId()})

	var createGiftResult struct {
		Id int `json:"id"`
	}
	_ = json.Unmarshal([]byte(result), &createGiftResult)
	return createGiftResult.Id
}

func claimGifts(bearer string, id int) {
	result := CallService("backend", "POST", "http://localhost:8080/api/gifts/claim", bearer, `{giftId: `+fmt.Sprint(id)+`}`, []string{})
	fmt.Println("Claimed gifts result: ", result)

	result = CallService("backend", "GET", "localhost:8080/api/gifts/available", bearer, "", []string{})
	fmt.Println("available: ", result)
}

type UserTokens struct {
	AccessToken  string `json:"accessToken"`
	RefreshToken string `json:"refreshToken"`
	CsrfToken    string `json:"csrfToken"`
}

type ErrorMessage struct {
	Why string `json:"why"`
}

func createUser(username string, password string, role string) *UserTokens {
	accessToken := FetchAccessToken(true)
	mail := username + "@fake-mail.com"
	response := CallService(
		"backend",
		"POST",
		"http://localhost:8080/auth/users/register",
		accessToken,
		`[{firstnames: `+username+`, lastname: `+username+`, username: `+username+`, password: `+password+`,  role: `+role+`, email: `+mail+`}]`,
		[]string{},
	)

	var errorMessage ErrorMessage

	_ = json.Unmarshal([]byte(response), &errorMessage)

	if errorMessage.Why != "" {
		if strings.Contains(errorMessage.Why, "Conflict") {
			fmt.Printf("User '%s' already exists. Proceeding.\n", username)
			return nil
		} else {
			panic("Unhandled error: " + errorMessage.Why + ". Bailing")
		}
	}

	var userTokens []UserTokens

	fmt.Printf("User %s created\n", username)
	_ = json.Unmarshal([]byte(response), &userTokens)
	return &userTokens[0]
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
		case "show-report":
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

func createRootAllocation() {
	CallService("backend", "POST", "localhost:8080/api/accounting/v2/rootAllocate", FetchAccessToken(true), `{
		"items": [
			{
				"category": {
					"name": "public-ip",
					"provider": "gok8s"
				},
				"end": 1767225599999,
				"quota": 1000,
				"start": 1735689600000
			},
			{
				"category": {
					"name": "storage",
					"provider": "gok8s"
				},
				"end": 1767225599999,
				"quota": 1000,
				"start": 1735689600000
			},
			{
				"category": {
					"name": "u1-standard",
					"provider": "gok8s"
				},
				"end": 1767225599999,
				"quota": 60000,
				"start": 1735689600000
			},
			{
				"category": {
					"name": "u2-standard",
					"provider": "gok8s"
				},
				"end": 1767225599999,
				"quota": 60000,
				"start": 1735689600000
			}
		]
	}`, []string{"-H", "Project: " + getRootProjectId()})
}

var providerId string = ""

func getProviderId() string {
	type Provider struct {
		Provider      string `json:"provider"`
		ProviderTitle string `json:"providerTitle"`
	}

	type ProviderResult struct {
		Items []Provider `json:"items"`
	}

	if providerId != "" {
		return providerId
	}

	var providerResult ProviderResult

	result := CallService("backend", "GET", "localhost:8080/api/providers/integration/browse?itemsPerPage=250", FetchAccessToken(true), "", []string{})
	_ = json.Unmarshal([]byte(result), &providerResult)

	for _, provider := range providerResult.Items {
		if provider.ProviderTitle == "gok8s" {
			providerId = provider.Provider
		}
	}

	return providerId
}

var rootProjectId string = ""

func getRootProjectId() string {
	type ProjectSpecification struct {
		Title string `json:"title"`
	}

	type Project struct {
		Id            string               `json:"id"`
		Specification ProjectSpecification `json:"specification"`
	}

	REQUIRED_PROJECT := "Provider gok8s"

	if rootProjectId != "" {
		return rootProjectId
	}
	result := CallService("backend", "GET", "localhost:8080/api/projects/v2/browse?itemsPerPage=250&includeFavorite=true&includeMembers=true&sortBy=favorite&sortDirection=descending&includeArchived=true", FetchAccessToken(true), "", []string{})
	var projectBrowseResponse struct {
		Items []Project `json:"items"`
	}
	_ = json.Unmarshal([]byte(result), &projectBrowseResponse)

	for _, project := range projectBrowseResponse.Items {
		if project.Specification.Title == REQUIRED_PROJECT {
			rootProjectId = project.Id
			return rootProjectId
		}
	}

	fmt.Printf("ERROR: Required provider '%s' not found. Exiting\n", REQUIRED_PROJECT)
	os.Exit(0)
	return ""
}
