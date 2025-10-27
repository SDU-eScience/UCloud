package main

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strings"
	"time"

	"ucloud.dk/launcher/pkg/launcher"
	"ucloud.dk/launcher/pkg/termio"
)

var repoRootPath string
var isHeadLess bool = false

func regexpCheck(s string) bool {
	exists, _ := regexp.MatchString("^[t][0-9]+$", s)
	return exists
}

func main() {
	args := os.Args

	if len(args) == 0 {
		log.Fatal("Bad invocation")
	}

	postExecPath := os.Args[1]

	opened, err := os.OpenFile(postExecPath, os.O_APPEND|os.O_CREATE|os.O_RDWR, 0666)
	launcher.HardCheck(err)
	launcher.PostExecFile = opened

	if len(args) > 1 && args[1] == "--help" {
		launcher.PrintHelp()
	}

	if _, err := os.Stat(".git"); err == nil {
		repoRootPath, _ = filepath.Abs(".")
	} else if _, e := os.Stat("../.git"); e == nil {
		repoRootPath, _ = filepath.Abs("..")
	} else {
		log.Fatal("Unable to determine repository root. Please run this script from the root of the repository.")
	}

	versionFile, err := os.Open(repoRootPath + "/backend/version.txt")
	launcher.SetRepoRoot(repoRootPath)
	launcher.HardCheck(err)

	scanner := bufio.NewScanner(versionFile)
	scanner.Scan()
	version := scanner.Text()

	if slices.Contains(args, "--version") {
		fmt.Println("UCloud", version)
		os.Exit(0)
	}

	width, _, _ := termio.SafeQueryPtySize()
	fmt.Printf("UCloud %s - Launcher tool \n", version)
	fmt.Println(strings.Repeat("-", width))

	// NOTE(Dan): initCurrentEnvironment() needs these to be set. We start out by running locally.
	launcher.GenerateProviders()

	shouldInitializeTestEnvironment := slices.Contains(args, "init") && slices.Contains(args, "--all-providers")

	isHeadLess = shouldInitializeTestEnvironment || (slices.Contains(args, "env") && slices.Contains(args, "delete")) ||
		(slices.Contains(args, "snapshot") && (slices.IndexFunc(args, regexpCheck) != -1))

	composeDir := filepath.Join(repoRootPath, ".compose")
	shouldStart := launcher.InitCurrentEnvironment(shouldInitializeTestEnvironment, composeDir).ShouldStartEnvironment

	var psLines []string
	var compose launcher.DockerCompose

	_ = termio.LoadingIndicator("Connecting to compose environment", func() error {
		compose = launcher.FindCompose()
		launcher.SetCompose(compose)

		returnedStringPair := compose.Ps(launcher.GetCurrentEnvironment()).ExecuteToText()

		psText := returnedStringPair.First
		failureText := returnedStringPair.Second
		if psText == "" && !shouldStart {
			fmt.Println("Unable to start docker compose in", launcher.GetCurrentEnvironment().Name())
			fmt.Println()
			fmt.Println(failureText)
			fmt.Println()
			fmt.Println("The error message above we got from docker compose. If this isn't helpful, "+
				"then try deleting this directory: ", launcher.GetCurrentEnvironment().Name())
			os.Exit(1)
		}

		for _, line := range strings.Split(psText, "\n") {
			if strings.TrimSpace(line) == "" {
				continue
			}
			if strings.HasPrefix(strings.ToLower(line), "name") {
				continue
			}
			if strings.HasPrefix(line, "---") {
				continue
			}
			psLines = append(psLines, line)
		}
		return nil
	})

	if shouldStart || len(psLines) <= 1 {
		launcher.GenerateComposeFile(true)
		answer, err := termio.ConfirmPrompt(
			"The environment "+launcher.GetCurrentEnvironment().Name()+" is not running. Do you want to start it?",
			termio.ConfirmValueTrue,
			0,
		)
		launcher.HardCheck(err)
		startConfirmed := shouldStart || answer

		if !startConfirmed {
			return
		}

		launcher.StartCluster(compose, false)

		if shouldStart {
			err = termio.LoadingIndicator(
				"Retrieving initial access token",
				func() error {
					for range 60 {
						success := launcher.FetchAccessToken() != ""
						if success {
							break
						}
						time.Sleep(1 * time.Second)
					}
					return nil
				},
			)
			launcher.HardCheck(err)

			launcher.ImportApps()

			fmt.Println()
			fmt.Println()
			fmt.Println("UCloud is now running. Yous should create a provider to get started. Select the " +
				"'Create provider...' entry below to do so.",
			)
		}
	}

	if shouldInitializeTestEnvironment {
		providers := launcher.AllProviders
		for _, provider := range providers {
			if strings.Contains(provider.Name(), "slurm") {
				// Do nothing
			} else {
				launcher.CreateProvider(provider.Name())
			}
		}
	}

	if len(args) > 2 {
		launcher.CliIntercept(args[2:])
	}

	toplevel := TopLevelMenu()
	selectedItem, err := toplevel.SelectSingle()
	launcher.HardCheck(err)
	switch selectedItem.Value {
	case "write-certs":
		{
			if len(args) > 1 {
				if args[1] != "" {
					launcher.WriteCerts(args[1])
				} else {
					launcher.PrintHelp()
				}
			}
		}

	case "install-certs":
		{
			launcher.InstallCerts()
		}
	case "ui":
		{
			launcher.InitializeServiceList()
			selectedService, err := ServiceMenu(false, false, true).SelectSingle()
			launcher.HardCheck(err)
			if selectedService.Value == "mainMenu" {
				launcher.PostExecFile.WriteString("\n " + launcher.GetRepoRoot().GetAbsolutePath() + "/launcher \n\n")
				os.Exit(0)
			}
			launcher.OpenUserInterface(selectedService.Value)
		}
	case "logs":
		{
			launcher.InitializeServiceList()
			item, err := ServiceMenu(false, true, false).SelectSingle()
			launcher.HardCheck(err)
			if item.Value == "mainMenu" {
				launcher.PostExecFile.WriteString("\n " + launcher.GetRepoRoot().GetAbsolutePath() + "/launcher \n\n")
				os.Exit(0)
			}
			service := launcher.ServiceByName(item.Value)
			CliHint("svc " + service.ContainerName() + " logs")
			launcher.OpenLogs(service.ContainerName())
		}
	case "shell":
		{
			launcher.InitializeServiceList()
			item, err := ServiceMenu(false, true, false).SelectSingle()
			launcher.HardCheck(err)
			if item.Value == "mainMenu" {
				launcher.PostExecFile.WriteString("\n " + launcher.GetRepoRoot().GetAbsolutePath() + "/launcher \n\n")
				os.Exit(0)
			}
			service := launcher.ServiceByName(item.Value)
			CliHint("svc " + service.ContainerName() + " sh")
			launcher.OpenShell(service.ContainerName())
		}
	case "providers":
		{
			launcher.GenerateComposeFile(true)

			for {
				configured := launcher.ListConfiguredProviders()
				providerMenu := CreateProviderMenu()
				multiple, err := providerMenu.SelectMultiple()
				launcher.HardCheck(err)
				filteredItems := []termio.MenuItem{}
				for _, selectedItem := range multiple {
					item := selectedItem.Value
					if !slices.Contains(configured, item) {
						filteredItems = append(filteredItems, termio.MenuItem{Value: item, Message: item})
					}
				}
				for len(filteredItems) == 0 {
					fmt.Println("You didn't select any providers. Use space to select a provider and enter to finish.")
					fmt.Println("Alternatively, you can exit with crtl + c.")
					fmt.Println()

					multiple, err = providerMenu.SelectMultiple()
					launcher.HardCheck(err)
					for _, selectedItem := range multiple {
						item := selectedItem.Value
						if !slices.Contains(configured, item) {
							filteredItems = append(filteredItems, termio.MenuItem{Value: item, Message: item})
						}
					}
				}

				for _, provider := range filteredItems {
					launcher.CreateProvider(provider.Value)
				}

				break
			}
		}
	case "services":
		{
			launcher.GenerateComposeFile(true)
			for {
				breakLoop := true
				selectedService, err := ServiceMenu(false, false, false).SelectSingle()
				launcher.HardCheck(err)
				if selectedService.Value == "mainMenu" {
					launcher.PostExecFile.WriteString("\n " + launcher.GetRepoRoot().GetAbsolutePath() + "/launcher \n\n")
					os.Exit(0)
				}
				service := launcher.ServiceByName(selectedService.Value)
				action, err := ServiceActionMenu().SelectSingle()
				launcher.HardCheck(err)
				switch action.Value {
				case "start":
					{
						CliHint("svc " + service.ContainerName() + " start")
						launcher.ServiceStart(service.ContainerName())
					}
				case "stop":
					{
						CliHint("svc " + service.ContainerName() + " stop")
						launcher.ServiceStop(service.ContainerName())
					}
				case "restart":
					{
						CliHint("svc " + service.ContainerName() + " restart [--follow]")
						launcher.ServiceStart(service.ContainerName())
						launcher.ServiceStart(service.ContainerName())
					}
				case "back":
					{
						breakLoop = false
					}
				case "mainMenu":
					{
						launcher.PostExecFile.WriteString("\n " + launcher.GetRepoRoot().GetAbsolutePath() + "/launcher \n\n")
						os.Exit(0)
					}

				}
				if breakLoop {
					break
				}
			}
		}

	case "environment":
		{
			launcher.GenerateComposeFile(true)

			envChoice, err := EnvironmentMenu().SelectSingle()
			launcher.HardCheck(err)
			switch envChoice.Value {
			case "stop":
				{
					CliHint("env stop")
					launcher.EnvironmentStop()
				}
			case "restart":
				{
					CliHint("env restart")
					launcher.EnvironmentRestart()
				}
			case "delete":
				{
					shouldDelete, _ := termio.ConfirmPrompt(
						"Are you sure you want to permanently delete the environment and all the data?",
						termio.ConfirmValueFalse,
						0,
					)
					if shouldDelete {
						CliHint("env delete")
						launcher.EnvironmentDelete(true)
					}
				}
			case "status":
				{
					CliHint("env status")
					launcher.EnvironmentStatus()
				}
			case "switch":
				{
					termio.LoadingIndicator("Shutting down virtual cluster", func() error {
						downCom := compose.Down(launcher.GetCurrentEnvironment(), false)
						downCom.SetStreamOutput()
						downCom.ExecuteToText()
						return nil
					})
					basePath := filepath.Join(repoRootPath, ".compose")
					env := launcher.SelectOrCreateEnvironment(basePath, false)
					launcher.InitIO()
					launcher.GetCurrentEnvironment().Child("..", true).Child(env, true)
					err := os.WriteFile(filepath.Join(basePath, "current.txt"), []byte(env), 0664)
					launcher.HardCheck(err)
				}
			case "mainMenu":
				{
					launcher.PostExecFile.WriteString("\n " + launcher.GetRepoRoot().GetAbsolutePath() + "/launcher \n\n")
					os.Exit(0)
				}

			}
		}
	case "help":
		{
			menu := termio.Menu{
				Prompt: "Select a topic",
				Items: []termio.MenuItem{
					{
						Value:   "start",
						Message: "Getting started",
					},
					{
						Value:   "usage",
						Message: "Using UCloud and basic troubleshooting",
					},
					{
						Value:   "debug",
						Message: "UCloud/Core databses and debugging",
					},
					{
						Value:   "providers",
						Message: "UCloud/IM and Providers",
					},
					{
						Value:   "mainMenu",
						Message: "Return to Main Menu",
					},
					{
						Value:   "exit",
						Message: "Exit",
					},
				},
			}

			chosenTopic := menu.Items[1]
			var nextTopic = chosenTopic.Value
			for nextTopic != "" {
				nextTopic = ""

				chosenTopic, err = menu.SelectSingle()
				launcher.HardCheck(err)
				nextTopic = chosenTopic.Value
				if nextTopic == "start" {
					fmt.Println(`
					Welcome! This is a small interactive help tool. For more in-depth documentation,
					please go here: https://docs.cloud.sdu.dk
	
					UCloud should now be up and running. If everything is working, then you should be able
					to access UCloud's frontend by opening the following web-page in your browser:
					https://ucloud.localhost.direct.
	
					The default credentials for this instance is:
	
					Username: user
					Password: mypassword
	
					This is an UCloud admin account which you can use to manage the UCloud instance. You
					can see which options are available to you from the "Admin" tab in the UCloud sidebar.
					`)

					moreTopics := termio.Menu{
						Prompt: "Select a topic",
						Items: []termio.MenuItem{
							{
								Value:   "troubleshoot",
								Message: "It is not working",
							},
							{
								Value:   "whatNext",
								Message: "What should I do now?",
							},
							{
								Value:   "back",
								Message: "Back",
							},
						},
					}
					chosenMoreTopic, err := moreTopics.SelectSingle()
					launcher.HardCheck(err)
					switch chosenMoreTopic.Value {
					case "troubleshoot":
						{
							fmt.Println(
								`
							There are a number of ways for you to troubleshoot a UCloud environment which
							is not working. Below we will try to guide you through a number of steps which
							might be relevant.
						`,
							)

							Suggestion(
								`
							Sometimes Docker is not able to correctly restart all the container of your
							system. You can try to restart your environment with "./launcher env restart".
		
							Following this, you should attempt to verify that the backend is running and
							not producing any errors. You can view the logs with "./launcher svc backend logs".
						`,
							)

							Suggestion(
								`
							Some development builds can be quite buggy or incompatible between versions.
							This can, for example, happen when in-development database schemas change
							drastically. In these cases, it is often easier to simply recreate your
							development environment. You can do this by running "./launcher env delete".
						`,
							)

							Suggestion(
								`
							If the issue persist after recreating your environment, then you might want to
							troubleshoot further by looking at the logs of the backend (./launcher svc backend logs)
							and any configured providers. Finally, you may wish to inspect the
							database (https://postgres.localhost.direct) and look for any problems here.
						`,
							)
						}
					case "whatNext":
						{
							nextTopic = "usage"
						}
					case "back":
						{
							break
						}
					}
				}
				if nextTopic == "usage" {
					fmt.Println(
						`
						UCloud has quite a number of features. If you have never used UCloud before, then
						we recommend reading the documentation here: https://docs.cloud.sdu.dk
						
						You can also select one of the topics below for common operations.
					`,
					)

					moreTopics := termio.Menu{
						Prompt: "Select a topic",
						Items: []termio.MenuItem{
							{
								Value:   "login",
								Message: "How do I login?",
							},
							{
								Value:   "createUser",
								Message: "How do I create a new user?",
							},
							{
								Value:   "createProject",
								Message: "How do I create a project?",
							},
							{
								Value:   "certExpired",
								Message: "The certificate has expired?",
							},
							{
								Value:   "noProducts",
								Message: "I don't se any machines when I attempt to start a job",
							},
							{
								Value:   "noFiles",
								Message: "I don't see any files when I attempt to access my files",
							},
							{
								Value:   "back",
								Message: "Back to main help menu",
							},
						},
					}

					var fallback = false
					for {
						if fallback {
							break
						}
						chosenMoreTopic, err := moreTopics.SelectSingle()
						launcher.HardCheck(err)
						switch chosenMoreTopic.Value {
						case "login":
							{
								fmt.Println(
									`
							You can access UCloud here: https://ucloud.localhost.direct	
                                        
							Username: user
							Password: mypassword
							
							See the "Getting started" topic for more information.						
						`)
							}
						case "createUser":
							{
								fmt.Println(
									`
							Using your admin user. You can select the "Admin" tab in the sidebar and
							create a new user from the "Create user" sub-menu.
						`,
								)
							}
						case "createProject":
							{
								fmt.Println(
									`
							You can create a new sub-project from the "Root" project.
                                            
							1. Click on "Manage projects" from the project selector.
							   You can find this next to the UCloud logo after you have logged in.
							2. Open the "Root" project by clicking on "Root" 
							   (alternatively, right click and select properties)
							3. Open the "Subprojects" menu
							4. Click "Create subproject" in the left sidebar
							
							At this point you should have a new project. Remember, your project cannot
							do anything until you have allocated resources to it. You should 
							automatically end up on a screen where you can ask for resources from your
							configured providers. If you don't see any resources, then make sure that
							you have configured a provider and that they are running.
						`,
								)
							}
						case "certExpired":
							{
								fmt.Println(
									`
							Try pulling from the git repository again. If that doesn't help and you
							are part of the development slack, try to ask @dan.
						`,
								)
							}
						case "noProducts", "noFiles":
							{
								fmt.Println(
									`
							Make sure that you have configured a provider and that it is running.
							You can create a provider by selecting "Create provider..." from the launcher
							menu.
	
							You can view the logs of the provider using "Open logs..." from the launcher.
						`,
								)

								fmt.Println(
									`
							You should also make sure that your current workspace has resources from
							this provider.
		
							You can check this by looking at "Resource Allocations" on the dashboard.
							If you don't see your provider listed here, then you don't have any
							resources allocated in this workspace.
		
							The easiest way to solve this, is by switching to the provider project. You
							can find this project from the project selector, which is next to the UCloud
							logo.
		
							If none of this works, then you may wish to look at the troubleshooting
							options from the "Getting started" help topic.
						`,
								)
							}
						case "back":
							{
								fallback = true
								break
							}
						}
					}
				}
				if nextTopic == "debug" {
					fmt.Println(
						`
						You can access the database from the web-interface here:
						https://postgres.localhost.direct.
		
						Alternatively, you can access it directly via:
		
						Host: localhost
						Port: 35432
						Username: postgres
						Password: postgrespassword
		
						You can view the logs from the database with: "./launcher svc postgres logs"
					`,
					)
				}

				if nextTopic == "mainMenu" {
					launcher.PostExecFile.WriteString("\n " + launcher.GetRepoRoot().GetAbsolutePath() + "/launcher \n\n")
					os.Exit(0)
				}

				if nextTopic == "providers" {
					//TODO
					fmt.Println("Not yet written")
				}

				if nextTopic == "exit" {
					nextTopic = ""
				}
			}
		}

	case "exit":
		{
			os.Exit(0)
		}
	}
}

func Suggestion(text string) {
	fmt.Println()
	fmt.Println(text)
	fmt.Println()
}

func CliHint(invocation string) {
	fmt.Println("You can also do this with: '" + "./launcher " + invocation + "'")
}

func EnvironmentMenu() termio.Menu {
	return termio.Menu{
		Prompt: "Select an action",
		Items: []termio.MenuItem{
			{
				Value:   "status",
				Message: "Display current environment status",
			},
			{
				Value:   "stop",
				Message: "Stop current environment",
			},
			{
				Value:   "restart",
				Message: "Restart current environment",
			},
			{
				Value:   "delete",
				Message: "Delete current environment",
			},
			{
				Value:   "switch",
				Message: "Switch current environment or create a new one",
			},
			{
				Value:     "navi",
				Message:   "Navigation",
				Separator: true,
			},
			{
				Value:   "mainMenu",
				Message: "Return to main menu",
			},
		},
	}
}

func ServiceActionMenu() termio.Menu {
	return termio.Menu{
		Prompt: "Select an action",
		Items: []termio.MenuItem{
			{
				Value:   "start",
				Message: "Start service",
			},
			{
				Value:   "stop",
				Message: "Stop service",
			},
			{
				Value:   "restart",
				Message: "Restart service",
			},
			{
				Value:     "navi",
				Message:   "Navigation",
				Separator: true,
			},
			{
				Value:   "back",
				Message: "Back to service overview",
			},
			{
				Value:   "mainMenu",
				Message: "Return to main menu",
			},
		},
	}
}
func CreateProviderMenu() termio.Menu {
	items := []termio.MenuItem{}
	alreadyConfigured := []termio.MenuItem{}
	configuredProviders := launcher.ListConfiguredProviders()
	allProviders := launcher.AllProviders

	for _, provider := range allProviders {
		contains := slices.Contains(configuredProviders, provider.Name())
		if contains {
			alreadyConfigured = append(alreadyConfigured, termio.MenuItem{
				Value:     provider.Name(),
				Message:   provider.Title() + " (Already configured)",
				Separator: false,
			})
		} else {
			items = append(items, termio.MenuItem{
				Value:     provider.Name(),
				Message:   provider.Title(),
				Separator: false,
			})
		}
	}
	if len(alreadyConfigured) > 0 {
		items = append(items, termio.MenuItem{
			Value:     "Dev",
			Message:   "Already Configured ",
			Separator: true,
		})
	}
	items = append(items, alreadyConfigured...)
	return termio.Menu{
		Prompt: "Select the providers you wish to configure (use space to select and enter to confirm selection)",
		Items:  items,
	}
}
func ServiceMenu(requireLogs bool, requireExec bool, requireAddress bool) termio.Menu {
	var filteredServices []launcher.Service
	for _, service := range launcher.RetrieveAllServices() {
		if (!requireLogs || service.LogsSupported()) &&
			(!requireExec || service.ExecSupported()) &&
			(!requireAddress || service.Address() != "") {
			filteredServices = append(filteredServices, service)
		}
	}

	var items []termio.MenuItem
	lastPrefix := ""
	for _, service := range filteredServices {
		foundParts := strings.Split(service.Title(), ": ")
		if len(foundParts) != 2 {
			continue
		}
		myPrefix := foundParts[0]
		if myPrefix != lastPrefix {
			items = append(items, termio.MenuItem{Value: service.ContainerName(), Message: myPrefix, Separator: true})
			lastPrefix = myPrefix
		}
		suffix := foundParts[1]
		items = append(items, termio.MenuItem{Value: service.ContainerName(), Message: suffix, Separator: false})
	}
	items = append(items, termio.MenuItem{
		Value:     "other",
		Message:   "Other",
		Separator: true,
	})
	items = append(items, termio.MenuItem{
		Value:   "mainMenu",
		Message: "Return to main menu",
	})
	return termio.Menu{
		Prompt: "Select a service",
		Items:  items,
	}
}

func TopLevelMenu() termio.Menu {
	items := []termio.MenuItem{}

	var message = ""
	items = append(items, termio.MenuItem{
		Value:     "Management",
		Message:   "Management",
		Separator: true,
	})

	if len(launcher.ListConfiguredProviders()) == 0 {
		message = "Create providers (Recommended)"
	} else {
		message = "Create providers"
	}

	items = append(items, termio.MenuItem{
		Value:   "providers",
		Message: message,
	})

	items = append(items, termio.MenuItem{
		Value:   "services",
		Message: "Manage services...",
	})

	items = append(items, termio.MenuItem{
		Value:   "environment",
		Message: "Manage environment...",
	})

	items = append(items, termio.MenuItem{
		Value:     "Development",
		Message:   "Development",
		Separator: true,
	})

	items = append(items, termio.MenuItem{
		Value:   "install-certs",
		Message: "Install certificates",
	})

	items = append(items, termio.MenuItem{
		Value:   "ui",
		Message: "Open user-interface...",
	})

	items = append(items, termio.MenuItem{
		Value:   "shell",
		Message: "Open shell to...",
	})

	items = append(items, termio.MenuItem{
		Value:   "logs",
		Message: "Open logs...",
	})

	items = append(items, termio.MenuItem{
		Value:     "Support",
		Message:   "Support",
		Separator: true,
	})

	items = append(items, termio.MenuItem{
		Value:   "test",
		Message: "Run a test suite...",
	})

	items = append(items, termio.MenuItem{
		Value:   "help",
		Message: "Get help with UCloud",
	})

	items = append(items, termio.MenuItem{
		Value:   "exit",
		Message: "Exit",
	})

	return termio.Menu{
		Prompt: "Select an item from the menu",
		Items:  items,
	}
}
