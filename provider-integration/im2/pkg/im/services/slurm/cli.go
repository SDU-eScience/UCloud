package slurm

import (
	"fmt"
	"os"
	"regexp"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/termio"
)

func HandleCli(command string) {
	ServiceConfig = cfg.Services.Slurm()
	switch command {
	case "connect":
		HandleConnectCommand()

	case "slurm-accounts":
		HandleSlurmAccountsCommand()

	case "users":
		HandleUsersCommand()

	case "projects":
		HandleProjectsCommand()

	case "jobs":
		HandleJobsCommand()

	case "drives":
		HandleDrivesCommand()

	case "allocations":
		HandleAllocationsCommand()
	}
}

func InitCliServer() {
	if cfg.Mode != cfg.ServerModeServer {
		log.Error("InitCliServer called in the wrong mode!")
		return
	}

	HandleProjectsCommandServer()
	HandleUsersCommandServer()
	HandleJobsCommandServer()
	HandleDrivesCommandServer()
	HandleAllocationsCommandServer()
}

func isListCommand(command string) bool {
	switch command {
	case "ls":
		fallthrough
	case "list":
		return true
	default:
		return false
	}
}

func isAddCommand(command string) bool {
	switch command {
	case "add":
		return true
	default:
		return false
	}
}

func isGetCommand(command string) bool {
	switch command {
	case "get":
		fallthrough
	case "retrieve":
		fallthrough
	case "stat":
		fallthrough
	case "view":
		return true
	default:
		return false
	}
}

func isDeleteCommand(command string) bool {
	switch command {
	case "del":
		fallthrough
	case "delete":
		fallthrough
	case "rm":
		fallthrough
	case "remove":
		return true
	default:
		return false
	}
}

func isReplaceCommand(command string) bool {
	switch command {
	case "replace":
		return true
	default:
		return false
	}
}

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

func cliValidateRegexes(argList ...any) error {
	// NOTE(Dan): Invalid usage is considered panic worthy since the build is fundamentally broken if
	// shipped like this.
	if len(argList)%3 != 0 {
		panic(fmt.Sprintf("invalid usage! arg list must follow a pattern of " +
			"(regexPtr **regexp.Regexp, argName string, regex string) tuples"))
	}

	for i := 0; i < len(argList); i += 3 {
		regexpPointer, ok := argList[i].(**regexp.Regexp)
		if regexpPointer != nil && !ok {
			panic(fmt.Sprintf("invalid usage in arg %v! arg list must follow a pattern of "+
				"(regexPtr **regexp.Regexp, argName string, regex string) tuples", i))
		}

		argName, ok := argList[i+1].(string)
		if !ok {
			panic(fmt.Sprintf("invalid usage in arg %v! arg list must follow a pattern of "+
				"(regexPtr **regexp.Regexp, argName string, regex string) tuples", i+1))
		}

		regex, ok := argList[i+2].(string)
		if !ok {
			panic(fmt.Sprintf("invalid usage in arg %v! arg list must follow a pattern of "+
				"(regexPtr **regexp.Regexp, argName string, regex string) tuples", i+2))
		}

		if regex != "" {
			parsed, err := regexp.Compile(regex)
			if err != nil {
				return fmt.Errorf("%s: %s", argName, err)
			}

			if regexpPointer != nil {
				*regexpPointer = parsed
			}
		}
	}

	return nil
}

func cliFormatTime(ts fnd.Timestamp) string {
	return ts.Time().Format("02 Jan 2006 15:04 MST")
}
