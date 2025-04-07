package launcher

import (
	"bufio"
	"io"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
)

type StringPair struct {
	First, Second string
}

func SoftCheck(e error) {
	if e != nil {
		log.Println(e)
	}
}

func HardCheck(e error) {
	if e != nil {
		panic(e)
	}
}

func readLines(path string) []string {
	inFile, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0666)
	HardCheck(err)
	r := bufio.NewReader(inFile)
	bytes := []byte{}
	lines := []string{}
	for {
		line, isPrefix, err := r.ReadLine()
		if err != nil {
			if err == io.EOF {
				break
			} else {
				panic(err)
			}
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

var UseCore2 = sync.OnceValue(func() bool {
	_, err := os.Stat(filepath.Join(currentEnvironment.GetAbsolutePath(), "../../.use-core2"))
	return err == nil
})

func GenerateComposeFile(doWriteFile bool) {
	providers := ListConfiguredProviders()

	var composeList = []ComposeService{
		&UCloudBackend{},
		&UCloudFrontend{},
		&GateWay{false},
	}

	if UseCore2() {
		composeList = append(composeList, &UCloudCore2{})
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

// TrimIndent removes the common leading whitespace from every line in a multi-line string
func TrimIndent(s string) string {
	lines := strings.Split(s, "\n")

	// Remove leading and trailing blank lines
	start := 0
	end := len(lines)
	for start < end && strings.TrimSpace(lines[start]) == "" {
		start++
	}
	for end > start && strings.TrimSpace(lines[end-1]) == "" {
		end--
	}
	lines = lines[start:end]

	// Find the minimum indent
	minIndent := -1
	for _, line := range lines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" {
			continue
		}
		leading := len(line) - len(strings.TrimLeft(line, " \t"))
		if minIndent == -1 || leading < minIndent {
			minIndent = leading
		}
	}

	// Remove the common indent
	for i, line := range lines {
		if len(line) >= minIndent {
			lines[i] = line[minIndent:]
		}
	}

	return strings.Join(lines, "\n")
}
