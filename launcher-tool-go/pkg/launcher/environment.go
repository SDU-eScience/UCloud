package launcher

import (
	"log"
	"os"
	"path/filepath"
	"strings"
)

var blacklistedEnvNames = [...]string{
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

		currentEnvironment = env
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

		localEnvironment = currentEnvironment

		//TODO commandFactory = RemoteExecutableCommandFactory(conn)
		//        fileFactory = RemoteFileFactory(conn)
	} else {
		//TODO commandFactory = RemoteExecutableCommandFactory(conn)
		//        fileFactory = RemoteFileFactory(conn)

		localEnvironment = currentEnvironment
		//TODO portAllocator
	}
}

func ListConfiguredProviders() []string {
	absPath, err := filepath.Abs(localEnvironment.Name())
	HardCheck(err)

	return readLines(filepath.Join(absPath, "providers.txt"))
}

func AddProvider(providerId string) {
	//TODO()
}

func ListAddons() map[string]map[string]string {
	//TODO()
}

func AddAddon(providerId string, addon string) {
	//TODO()
}
