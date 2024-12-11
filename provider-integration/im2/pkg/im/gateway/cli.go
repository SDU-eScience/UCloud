package gateway

import (
	"os"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/pkg/util"
)

func HandleCli(pluginName string) bool {
	if pluginName != "gateway" {
		return false
	}

	if os.Getuid() != 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This command must be run as root!")
		os.Exit(1)
	}

	command := ""
	if len(os.Args) >= 3 {
		command = os.Args[2]
	}

	switch command {
	case "dump":
		result, err := ipcGwDumpConfiguration.Invoke(util.EmptyValue)
		cliHandleError("dumping configuration", err)

		termio.WriteLine(result)

	default:
		if command == "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Missing command")
		} else {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Unknown command: %s", command)
		}
	}

	return true
}

// TODO move this?
func cliHandleError(context string, err error) {
	if err == nil {
		return
	}

	if context == "" {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "%s", context, err.Error())
	} else {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "%s: %s", context, err.Error())
	}

	os.Exit(1)
}
