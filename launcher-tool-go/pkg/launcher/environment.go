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

var localEnvironment *os.File
var currentEnvironment LFile
var environmentIsRemote bool
var portAllocator PortAllocator
var composeName string
var repoRoot LocalFile

func SelectOrCreateEnvironment(baseDirPath string, initTest bool) string {
	files, err := os.ReadDir(baseDirPath)
	HardCheck(err)
	var alternativeEnvironments []os.DirEntry
	for _, file := range files {
		if file.IsDir() && slices.Contains(blacklistedEnvNames, file.Name()) {
			alternativeEnvironments = append(alternativeEnvironments, file)
		}
	}
	if len(alternativeEnvironments) > 0 {
		menu := termio.Menu{
			Prompt: "Select an environment",
			Items:  nil,
		}

		for _, envPath := range alternativeEnvironments {
			fullPath, err := filepath.Abs(envPath.Name())
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
			HardCheck(err)
			env := LocalFile{File: file}
			currentEnvironment = env
			_, err = os.Stat(selected.Value)
			environmentIsRemote = err != nil
			return filepath.Base(selected.Value)
		}
	}

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

	var newEnvironment string
	for true {
		var env string
		if initTest {
			env = "test"
		} else {
			env = queryText(prompt, "Select a name for your environment", "default")
		}
		if slices.Contains(blacklistedEnvNames, env) {
			fmt.Println("Illegal name. Try a different one.")
			continue
		}
		stat, err := os.Stat(repoRoot.GetAbsolutePath())
		if err == nil {
			fmt.Println("This environment already exists. Please try a different name.")
			continue
		}
		newEnvironment = env
		break
	}
	var operator termio.MenuItem
	if initTest {
		operator = local
	} else {
		operator = remote
	}
	switch operator {
	case local:
		{
			printExplanation("The following is expected of your machine:")
			println()
			printExplanation("- The machine should run Linux or macOS")
			printExplanation("- Docker and Docker Compose (either through `docker compose` or `docker-compose`)")
			printExplanation("- Your user must be in the docker group and be able to issue docker commands without sudo")
			println()

			path := repoRoot.GetAbsolutePath() + ".compose"
			file, err := os.Open(path)
			HardCheck(err)
			env := LocalFile{path: path, File: file}
			env.MkDirs()

			currentEnvironment = env
			_, err = os.Stat(env.GetAbsolutePath() + "remote")
			environmentIsRemote = err == nil

			return filepath.Base(newEnvironment)
		}
	case remote:
		{
			printExplanation("The following is expected of the remote machine:")
			println()
			printExplanation("- The machine should run Linux")
			printExplanation("- Docker and Docker Compose (either through `docker compose` or `docker-compose`)")
			printExplanation("- Your user must be in the docker group and be able to issue docker commands without sudo")
			println()

			hostname := queryText(prompt, "What is the hostname/IP of this machine? (e.g. machine42.example.com)")
			username := queryText(prompt, "What is your username on this machine? (e.g. janedoe)")

			path := repoRoot.GetAbsolutePath() + ".compose"
			file, err := os.Open(path)
			HardCheck(err)
			env := LocalFile{path: path, File: file}
			env.MkDirs()

			currentEnvironment = env
			environmentIsRemote = true

			env.Child("remote").WriteText(username + "@" + hostname)
			return filepath.Base(newEnvironment)
		}
	default:
		panic("unreachable")

	}

}

type InitEnvironmentResult struct {
	shouldStartEnvironment bool
}

func InitCurrentEnvironment(shouldInitializeTestEnvironment bool, baseDir string) InitEnvironmentResult {
	if shouldInitializeTestEnvironment {
		HardCheck(os.RemoveAll(baseDir))
		HardCheck(os.Mkdir(baseDir, os.ModeDir))
	}

	currentText, err := os.ReadFile(filepath.Join(baseDir, "current.txt"))
	HardCheck(err)
	currentEnvironmentName := string(currentText)
	var currentIsRemote bool
	if currentEnvironmentName != "" {
		remoteName := filepath.Join(baseDir, currentEnvironmentName, "remote")
		_, statError := os.Stat(remoteName)
		currentIsRemote = statError != nil
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
		println(`
			No active environment detected!

			An environment is a complete installation of UCloud. It contains all the files and software required to
		run UCloud. You can have multiple environments, all identified by a name, but only one environment
		running at any given time.

			In the next step, we will ask you to select a name for your environment. The name doesn't have any
			meaning and you can simply choose the default by pressing enter.
			`,
		)
		println()
		SelectOrCreateEnvironment(baseDir, shouldInitializeTestEnvironment)
	} else {
		//TODO Better printing BRIAN
		println("Active environment: " + env.Name())
		println()
		path, err := filepath.Abs(env.Name())
		HardCheck(err)
		currentEnvironment = LocalFile{path: path, File: env}
		environmentIsRemote = currentIsRemote
	}

	isNew := env == nil
	InitIO(isNew)

	currentName := []byte(currentEnvironment.Name())
	err = os.WriteFile(filepath.Join(baseDir, "current.txt"), currentName, 0644)
	HardCheck(err)

	return InitEnvironmentResult{shouldStartEnvironment: isNew}
}

func InitIO(isNew bool) {
	if environmentIsRemote {
		baseDir, _ := filepath.Abs(currentEnvironment.Name())
		fileContent, err := os.ReadFile(filepath.Join(baseDir, "remote"))
		HardCheck(err)
		lineSplit := strings.Split(string(fileContent), "@")
		if len(lineSplit) != 2 {
			log.Fatal("Unable to parse remote details from environment: $baseDir. Try deleting this folder.")
		}

		//TODO SSHCONNECTION

		localEnvironment = currentEnvironment.(LocalFile)

		//TODO commandFactory = RemoteExecutableCommandFactory(conn)
		//        fileFactory = RemoteFileFactory(conn)
	} else {
		//TODO commandFactory = RemoteExecutableCommandFactory(conn)
		//        fileFactory = RemoteFileFactory(conn)

		localEnvironment = currentEnvironment.(LocalFile)
		//TODO portAllocator
	}
}

func ListConfiguredProviders() []string {
	absPath, err := filepath.Abs(localEnvironment.Name())
	HardCheck(err)

	return readLines(filepath.Join(absPath, "providers.txt"))
}

func AddProvider(providerId string) {
	f, err := os.OpenFile(localEnvironment.Name(), os.O_APPEND, 0644)
	HardCheck(err)
	_, err = f.WriteString(providerId + "\n")
	HardCheck(err)
}

func ListAddons() map[string]map[string]string {
	result := map[string]map[string]string{}
	abs, err := filepath.Abs(localEnvironment.Name())
	HardCheck(err)
	lines := readLines(abs + "/provider-addons.txt")
	for _, line := range lines {
		split := strings.Split(line, "/")
		providerId := split[len(split)-2]
		addon := split[len(split)-1]
		m := result[providerId]
		m[addon] = addon
		result[providerId] = m
	}
	return result
}

func AddAddon(providerId string, addon string) {
	f, err := os.OpenFile(localEnvironment.Name()+"/provider-addons.txt", os.O_APPEND, 0644)
	HardCheck(err)
	_, err = f.WriteString(providerId + "/" + addon + "\n")
	HardCheck(err)
}
