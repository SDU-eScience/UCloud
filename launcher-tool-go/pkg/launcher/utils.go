package launcher

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

var commandType ExecutableCommandInterface

type StringPair struct {
	First, Second string
}

func SoftCheck(e error) {
	if e != nil {
		fmt.Println(e)
	}
}

func HardCheck(e error) {
	if e != nil {
		panic(e)
	}
}

func readLines(path string) []string {
	inFile, _ := os.Open(path)
	r := bufio.NewReader(inFile)
	bytes := []byte{}
	lines := []string{}
	for {
		line, isPrefix, err := r.ReadLine()
		if err != nil {
			break
		}
		bytes = append(bytes, line...)
		if !isPrefix {
			str := strings.TrimSpace(string(bytes))
			if len(str) > 0 {
				lines = append(lines, str)
				bytes = []byte{}
			}
		}
	}
	if len(bytes) > 0 {
		lines = append(lines, string(bytes))
	}
	return lines
}

func ExecuteCommand(args []string, allowFailure bool) string {
	cmd := exec.Command(args[0], args[1:]...)
	stdout, err := cmd.Output()
	if err != nil && !allowFailure {
		log.Fatal("Error executing command: ", err)
	}
	return string(stdout)
}

func DebugCommandsGiven() bool {
	return os.Getenv("DEBUG_COMMANDS") != ""
}

func GenerateComposeFile(doWriteFile bool) {
	providers := ListConfiguredProviders()

	var composeList = []ComposeService{
		UCloudBackend{},
		UCloudFrontend{},
		GateWay{false},
	}

	for _, provider := range providers {
		composeList = append(composeList, ProviderFromName(provider))
	}

	Environment{
		name:        filepath.Base(currentEnvironment.GetAbsolutePath()),
		repoRoot:    currentEnvironment.Child("../../", true),
		doWriteFile: doWriteFile,
	}.CreateComposeFile(
		composeList,
	)
}

func InitializeServiceList() {
	GenerateComposeFile(false)
}
