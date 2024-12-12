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

var repoRoot string
var isHeadLess bool = false
var postExecFile *os.File

func regexpCheck(s string) bool {
	exists, _ := regexp.MatchString("^[t][0-9]+$", s)
	return exists
}

func main() {
	args := os.Args

	if len(args) == 0 {
		log.Fatal("Bad invocation")
	}

	postExecPath := os.Args[0]
	postExecFile, _ = os.Open(postExecPath)

	if len(args) > 1 && args[1] == "--help" {
		launcher.PrintHelp()
	}

	if _, err := os.Stat(".git"); err == nil {
		repoRoot, _ = filepath.Abs(".")
	} else if _, e := os.Stat("../.git"); e == nil {
		repoRoot, _ = filepath.Abs("..")
	} else {
		log.Fatal("Unable to determine repository root. Please run this script from the root of the repository.")
	}

	versionFile, err := os.Open(repoRoot + "/backend/version.txt")
	launcher.HardCheck(err)

	scanner := bufio.NewScanner(versionFile)
	scanner.Scan()
	version := scanner.Text()

	if slices.Contains(args, "--version") {
		fmt.Println("UCloud", version)
		os.Exit(0)
	}

	fmt.Printf("UCloud %s - Launcher tool \n", version)
	fmt.Println("-")

	// NOTE(Dan): initCurrentEnvironment() needs these to be set. We start out by running locally.
	//TODO() commandFactory = LocalExecutableCommandFactory()
	//TODO() fileFactory = LocalFileFactory()

	shouldInitializeTestEnvironment := slices.Contains(args, "init") && slices.Contains(args, "--all-providers")

	isHeadLess = shouldInitializeTestEnvironment || (slices.Contains(args, "env") && slices.Contains(args, "delete")) ||
		(slices.Contains(args, "snapshot") && (slices.IndexFunc(args, regexpCheck) != -1))

	shouldStart := launcher.InitCurrentEnvironment(shouldInitializeTestEnvironment, repoRoot).ShouldStartEnvironment

	compose := launcher.FindCompose()
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

	var psLines []string
	for _, line := range strings.Split(psText, "\n") {
		if strings.TrimSpace(line) == "" {
			continue
		}
		if len(line) >= 4 && strings.ToLower(strings.TrimSpace(line)[:4]) != "name" {
			continue
		}
		if len(line) >= 3 && strings.TrimSpace(line)[:3] != "---" {
			continue
		}
		psLines = append(psLines, line)
	}

	if shouldStart || len(psLines) <= 1 {
		launcher.GenerateComposeFile(true)
		answer, err := termio.ConfirmPrompt(
			"The environment "+launcher.GetCurrentEnvironment().Name()+"is not running. Do you want to start it?",
			termio.ConfirmValueTrue,
			0,
		)
		launcher.HardCheck(err)
		startConfirmed := shouldStart || answer

		if !startConfirmed {
			return
		}

		startCluster(compose, false)

		if shouldStart {
			termio.LoadingIndicator(
				"Retrieving initial access token",
				func(output *os.File) error {
					for attempt := range 10 {
						success := FetchAccessToken.isSuccess
						if success {
							break
						}
						time.Sleep(1 * time.Second)
					}
					return nil
				},
			)

		}

		launcher.Commands{}.ImportApps()

		fmt.Println()
		fmt.Println()
		fmt.Println("UCloud is now running. Yous should create a provider to get started. Select the " +
			"'Create provider...' entry below to do so."
		)
	}

	if shouldInitializeTestEnvironment {
		providers := launcher.AllProviders
		for _, provider := range providers {
			if strings.Contains(provider.Name(), "slurm") {
				// Do nothing
			} else {
				launcher.Commands{}.CreateProvider(provider.Name())
			}
		}
	}

	launcher.CliIntercept(args[1:])

	toplevel := TopLevelMenu()
	commands := launcher.Commands{}
	//TODO correct switch?
	switch toplevel.Prompt {
	case "Enable port-forwarding (REQUIRED)":
		{
			CliHint("port-forward")
			commands.PortForward()
		}

	case "Open user-interface...":
		{
			launcher.InitializeServiceList()
			commands.OpenUserInterface(ServiceMenu().Prompt)
		}
	case "logs":
		{
			launcher.InitializeServiceList()
			item := ServiceMenu().Prompt
			CliHint("svc " + item + " logs")
			commands.OpenLogs(item)
		}
	case "shell":
		{
			launcher.InitializeServiceList()
			item := ServiceMenu().Prompt
			CliHint("svc " + item + " sh")
			commands.OpenShell(item)
		}
	case "Create provider...": {
		launcher.GenerateComposeFile(true)
		launcher.SyncRepository()

		for {
			configured := launcher.ListConfiguredProviders()
			selectedProviders := CreateProviderMenu() //TODO
			if (len(selectedProviders) == 0) {
				fmt.Println("You didn't select any providers. Use space to select a provider and enter to finish.")
				fmt.Println("Alternatively, you can exit with crtl + c.")
				fmt.Println()
			}

			for provider := range selectedProviders {
				commands.CreateProvider(provider)
			}

			break
		}
	}
	case "services": {
		launcher.GenerateComposeFile(true)
		launcher.SyncRepository()
		service := launcher.ServiceByName(ServiceMenu().Prompt)
		switch ServiceActionMenu().Prompt {
		case "start":
			{
				CliHint("svc " + service.ContainerName() + " start")
				commands.ServiceStart(service.ContainerName())
			}
		case "stop":
			{
				CliHint("svc " + service.ContainerName() + " stop")
				commands.ServiceStop(service.ContainerName())
			}
		case "restart":
			{
				CliHint("svc " + service.ContainerName() + " restart [--follow]")
				commands.ServiceStart(service.ContainerName())
				commands.ServiceStart(service.ContainerName())
			}
		}
	}

	case "test": {
		launcher.GenerateComposeFile(true)
		launcher.SyncRepository()
		println("Not yet implemented") //TODD
	}

	case "environment": {
		launcher.GenerateComposeFile(true)
		launcher.SyncRepository()

		switch EnvironmentMenu().Prompt {
		case "stop":
			{
				CliHint("env stop")
				commands.EnvironmentStop()
			}
		case "restart":
			{
				CliHint("env restart")
				commands.EnvironmentRestart()
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
					commands.EnvironmentDelete(true)
				}
			}
		case "status":
			{
				CliHint("env status")
				commands.EnvironmentStatus()
			}
		case "switch":
			{
				termio.LoadingIndicator("Shutting down virtual cluster", func(output *os.File) error {
					downCom := compose.Down(launcher.GetCurrentEnvironment(), false)
					downCom.SetStreamOutput()
					downCom.ExecuteToText()
					return nil
				})
				basePath := filepath.Join(launcher.GetCurrentEnvironment().GetAbsolutePath(), ".compose")
				env := launcher.SelectOrCreateEnvironment(basePath, false)
				launcher.InitIO(true)
				launcher.GetCurrentEnvironment().Child("..").Child(env)
				err := os.WriteFile(filepath.Join(basePath, "current.txt"), []byte(env), 664)
				launcher.HardCheck(err)
			}
		}
	}
	case "help": {
		menu := termio.Menu{
			"Select a topic",
			[]termio.MenuItem{
				{
					Value:   "start",
					Message: "Getting started",
				},
				{
					Value:   "usage",
					Message: "Using UCloud and basic troubleshooting",
				},
				{
					Value: "debug",
					Message: "UCloud/Core databses and debugging",
				},
				{
					Value: "providers",
					Message: "UCloud/IM and Providers",
				},
			},
		}

		var nextTopic = menu.Prompt
		for {
			if nextTopic == "" {
				break
			}
			nextTopic = ""
			switch menu.Prompt {
			case "start":
				{
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
						},
					}
					switch moreTopics.Prompt {
					case "troubleshoot":
						{
							fmt.Println(
								`
							There are a number of ways for you to troubleshoot a UCloud environment which
							is not working. Below we will try to guide you through a number of steps which
							might be relevant.
						`,
							)

							if launcher.GetEnvironmentIsRemote() {
								Suggestion(
									`
								It looks like your current environment is using a remote machine. Please 
								make sure that port-forwarding is configured and working correctly. You can 
								access port-forwarding from the interactive menu.
							`,
								)
							}

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

					}
				}
			case "usage":
				{
					fmt.Println(
						`
						UCloud has quite a number of features. If you have never used UCloud before, then
						we recommend reading the documentation here: https://docs.cloud.sdu.dk
						
						You can also select one of the topics below for common operations.
					`,
					)

					moreTopics := termio.Menu{
						"Select a topic",
						[]termio.MenuItem{
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
						}
					}

					switch moreTopics.Prompt {
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
					}
				}

			case "debug":
				{
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

			case "providers":
				{
					//TODO
					fmt.Println("Not yet written")
				}

			}
		}
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

func EnvironmentMenu() termio.Menu {}
func ServiceActionMenu() termio.Menu {}
func CreateProviderMenu() termio.MultipleChoice {}
func ServiceMenu() termio.Menu {}

func TopLevelMenu() termio.Menu {
	menu := termio.Menu{
		Prompt: "Select an item from the menu",
		Items:  nil,
	}

	return menu
}
