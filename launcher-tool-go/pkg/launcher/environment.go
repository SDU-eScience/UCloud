package launcher

import (
	"fmt"
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
var portAllocator PortAllocator
var composeName string
var repoRoot LocalFile

func SetRepoRoot(path string) {
	repoRoot = LocalFile{path}
}

func GetRepoRoot() LocalFile {
	return repoRoot
}

func GetCurrentEnvironment() LFile {
	return currentEnvironment
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

		return filepath.Base(newEnvironment)
	}
}

type InitEnvironmentResult struct {
	ShouldStartEnvironment bool
}

func InitCurrentEnvironment(shouldInitializeTestEnvironment bool, baseDir string) InitEnvironmentResult {
	var err error
	if shouldInitializeTestEnvironment {
		_ = os.RemoveAll(baseDir)
	}
	_ = os.Mkdir(baseDir, 0700)

	currentText, _ := os.ReadFile(filepath.Join(baseDir, "current.txt"))
	currentEnvironmentName := string(currentText)

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
	}

	isNew := env == nil
	InitIO()

	currentName := []byte(currentEnvironment.Name())
	err = os.WriteFile(filepath.Join(baseDir, "current.txt"), currentName, 0644)
	HardCheck(err)

	return InitEnvironmentResult{ShouldStartEnvironment: isNew}
}

func InitIO() {
	localEnvironment = currentEnvironment.(LocalFile)
	portAllocator = Direct{}
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
