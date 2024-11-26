package main

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"ucloud.dk/launcher/pkg/launcher"
)

var repoRoot string
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
	//postExecPath := os.Args[0]
	//postExecFile := os.ReadFile(postExecPath)

	if len(args) > 1 && args[1] == "--help" {
		launcher.PrintHelp()
	}

	//TODO() AnsiConsole.systemInstall()

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

	shouldStart := launcher.InitCurrentEnvironment(shouldInitializeTestEnvironment, repoRoot)
	println(shouldStart)
	compose = launcher.FindCompose()

	compose.Up()

}
