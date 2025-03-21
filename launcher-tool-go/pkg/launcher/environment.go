package launcher

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"ucloud.dk/launcher/pkg/termio"
)

var blacklistedEnvNames = []string{
	"postgres",
	"passwd",
	"home",
	"cluster-home",
	"im-config",
}

var localEnvironment LocalFile
var currentEnvironment LFile
var environmentIsRemote bool
var portAllocator PortAllocator
var composeName string
var repoRoot LocalFile

func SetRepoRoot(path string) {
	repoRoot = LocalFile{path}
}

func GetRepoRoot() LocalFile {
	return repoRoot
}

func GetEnvironmentIsRemote() bool {
	return environmentIsRemote
}

func SetEnvironmentIsRemote(isRemote bool) {
	environmentIsRemote = isRemote
}

func GetLocalEnvironment() LocalFile {
	return localEnvironment
}

func SetLocalEnvironment(environment LocalFile) {
	localEnvironment = environment
}

func GetCurrentEnvironment() LFile {
	return currentEnvironment
}

func SetCurrentEnvironment(environment LFile) {
	currentEnvironment = environment
}

func SelectOrCreateEnvironment(baseDirPath string, initTest bool) string {
	files, err := os.ReadDir(baseDirPath)
	HardCheck(err)
	var alternativeEnvironments []os.DirEntry
	for _, file := range files {
		if file.IsDir() && !slices.Contains(blacklistedEnvNames, file.Name()) {
			alternativeEnvironments = append(alternativeEnvironments, file)
		}
	}
	if len(alternativeEnvironments) > 0 {
		menu := termio.Menu{
			Prompt: "Select an environment",
			Items:  nil,
		}

		for _, envPath := range alternativeEnvironments {
			fullPath, err := filepath.Abs(filepath.Join(".compose", envPath.Name()))
			HardCheck(err)
			menu.Items = append(menu.Items, termio.MenuItem{
				Value:   fullPath,
				Message: envPath.Name(),
			})
		}

		menu.Items = append(menu.Items, termio.MenuItem{
			Value:   "new",
			Message: "Created new environment",
		})

		selected, err := menu.SelectSingle()
		HardCheck(err)
		if selected.Message != "Create new environment" && selected.Value != "new" {
			file, err := os.Open(selected.Value)
			defer file.Close()
			HardCheck(err)

			env := NewFile(selected.Value)
			currentEnvironment = env
			_, err = os.Stat(selected.Value)
			environmentIsRemote = err != nil
			return filepath.Base(selected.Value)
		}
	}

	var newEnvironment string
	for {
		var env string
		if initTest {
			env = "test"
		} else {
			env = termio.TextPrompt("Select a name for your environment", "default")
		}
		if slices.Contains(blacklistedEnvNames, env) {
			fmt.Println("Illegal name. Try a different one.")
			continue
		}

		_, err := os.Stat(filepath.Join(repoRoot.GetAbsolutePath(), ".compose", env))
		if err == nil {
			fmt.Println("This environment already exists. Please try a different name.")
			continue
		}
		newEnvironment = env
		break
	}
	var operator termio.MenuItem

	local := termio.MenuItem{
		Value:   "local",
		Message: "Local environment on this machine",
	}

	remote := termio.MenuItem{
		Value:   "remote",
		Message: "Remote environment on some other machine (via SSH)",
	}

	localOrRemoteMenu := termio.Menu{
		Prompt: "Should this be a local or remote environment?",
		Items:  []termio.MenuItem{local, remote},
	}

	if initTest {
		operator = local
	} else {
		item, err := localOrRemoteMenu.SelectSingle()
		HardCheck(err)
		if item.Value == local.Value {
			operator = local
		} else {
			operator = remote
		}
	}
	switch operator {
	case local:
		{
			fmt.Println("The following is expected of your machine:")
			fmt.Println()
			fmt.Println("- The machine should run Linux or macOS")
			fmt.Println("- Docker and Docker Compose (either through `docker compose` or `docker-compose`)")
			fmt.Println("- Your user must be in the docker group and be able to issue docker commands without sudo")
			fmt.Println()

			path := filepath.Join(repoRoot.GetAbsolutePath(), ".compose", newEnvironment)
			env := NewFile(path)
			env.MkDirs()

			currentEnvironment = env
			_, err = os.Stat(filepath.Join(env.GetAbsolutePath(), "remote"))
			environmentIsRemote = err == nil

			return filepath.Base(newEnvironment)
		}
	case remote:
		{
			fmt.Println("The following is expected of the remote machine:")
			fmt.Println()
			fmt.Println("- The machine should run Linux")
			fmt.Println("- Docker and Docker Compose (either through `docker compose` or `docker-compose`)")
			fmt.Println("- Your user must be in the docker group and be able to issue docker commands without sudo")
			fmt.Println()

			hostname := termio.TextPrompt("What is the hostname/IP of this machine? (e.g. machine42.example.com)", "machine42.example.com")
			username := termio.TextPrompt("What is your username on this machine? (e.g. janedoe)", "janedoe")

			path := repoRoot.GetAbsolutePath() + ".compose"
			file, err := os.Open(path)
			defer file.Close()
			HardCheck(err)
			env := NewFile(path)
			env.MkDirs()

			currentEnvironment = env
			environmentIsRemote = true

			env.Child("remote", false).WriteText(username + "@" + hostname)
			return filepath.Base(newEnvironment)
		}
	default:
		panic("unreachable")

	}

}

type InitEnvironmentResult struct {
	ShouldStartEnvironment bool
}

func InitCurrentEnvironment(shouldInitializeTestEnvironment bool, baseDir string) InitEnvironmentResult {
	if shouldInitializeTestEnvironment {
		HardCheck(os.RemoveAll(baseDir))
		HardCheck(os.Mkdir(baseDir, os.ModeDir))
	}

	_, err := os.OpenFile(filepath.Join(baseDir, "current.txt"),
		os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	currentText, err := os.ReadFile(filepath.Join(baseDir, "current.txt"))
	HardCheck(err)

	currentEnvironmentName := string(currentText)
	var currentIsRemote bool
	if currentEnvironmentName != "" {
		remoteName := filepath.Join(baseDir, currentEnvironmentName, "remote")
		stat, _ := os.Stat(remoteName)
		currentIsRemote = stat != nil
	} else {
		currentIsRemote = false
	}

	var env *os.File
	if currentEnvironmentName == "" {
		env = nil
	} else {
		env, err = os.Open(filepath.Join(baseDir, currentEnvironmentName))
		if err != nil {
			env = nil
		}
	}
	if env == nil {
		fmt.Println(`
No active environment detected!

An environment is a complete installation of UCloud. It contains all the files and software required to
run UCloud. You can have multiple environments, all identified by a name, but only one environment
running at any given time.

In the next step, we will ask you to select a name for your environment. The name doesn't have any
meaning and you can simply choose the default by pressing enter.
		`,
		)
		fmt.Println()
		SelectOrCreateEnvironment(baseDir, shouldInitializeTestEnvironment)
	} else {
		fmt.Println("Active environment: " + env.Name())
		fmt.Println()
		path, err := filepath.Abs(env.Name())
		HardCheck(err)
		currentEnvironment = NewFile(path)
		environmentIsRemote = currentIsRemote
	}

	isNew := env == nil
	InitIO(isNew)

	currentName := []byte(currentEnvironment.Name())
	err = os.WriteFile(filepath.Join(baseDir, "current.txt"), currentName, 0644)
	HardCheck(err)

	return InitEnvironmentResult{ShouldStartEnvironment: isNew}
}

func InitIO(isNew bool) {
	if environmentIsRemote {
		baseDir, _ := filepath.Abs(currentEnvironment.Name())
		fileContent, err := os.ReadFile(filepath.Join(baseDir, "remote"))
		HardCheck(err)
		lineSplit := strings.Split(string(fileContent), "@")
		if len(lineSplit) != 2 {
			log.Fatal("Unable to parse remote details from environment: " + baseDir + ". Try deleting this folder.")
		}

		//TODO SSHCONNECTION

		localEnvironment = currentEnvironment.(LocalFile)

		remoteRepoRoot := NewFile("ucloud")
		if isNew {
			SyncRepository()
		}
		remoteEnvironment := remoteRepoRoot.Child(".compose/"+currentEnvironment.Name(), true)
		portAllocator = Remapped{
			portAllocator:  0,
			allocatedPorts: nil,
		}
		composeName = currentEnvironment.Name() + "_" + "CONNECTION" //TODO
		currentEnvironment = remoteEnvironment
	} else {
		localEnvironment = currentEnvironment.(LocalFile)
		portAllocator = Direct{}
	}
}

func ListConfiguredProviders() []string {
	return readLines(filepath.Join(localEnvironment.GetAbsolutePath(), "providers.txt"))
}

func AddProvider(providerId string) {
	f, err := os.OpenFile(filepath.Join(localEnvironment.GetAbsolutePath(), "providers.txt"), os.O_APPEND|os.O_CREATE|os.O_RDWR, 0666)
	HardCheck(err)
	_, err = f.WriteString(providerId + "\n")
	HardCheck(err)
}

func ListAddons() map[string]map[string]string {
	result := map[string]map[string]string{}
	path := filepath.Join(localEnvironment.GetAbsolutePath(), "provider-addons.txt")
	file, err := os.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_RDWR, 0666)
	defer file.Close()
	HardCheck(err)
	lines := readLines(path)
	for _, line := range lines {
		if strings.TrimSpace(line) == "" {
			continue
		}
		split := strings.Split(line, "/")
		providerId := split[len(split)-2]
		addon := split[len(split)-1]
		m := result[providerId]
		if m == nil {
			m = make(map[string]string)
			m[addon] = addon
		} else {
			m[addon] = addon
		}
		result[providerId] = m
	}
	return result
}

func AddAddon(providerId string, addon string) {
	f, err := os.OpenFile(filepath.Join(localEnvironment.GetAbsolutePath(), "provider-addons.txt"), os.O_APPEND|os.O_CREATE|os.O_RDWR, 0666)
	HardCheck(err)
	_, err = f.WriteString(providerId + "/" + addon + "\n")
	HardCheck(err)
}
