package cli

import (
	"fmt"
	"os"
	"regexp"

	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/termio"
)

func IsListCommand(command string) bool {
	switch command {
	case "ls":
		fallthrough
	case "list":
		return true
	default:
		return false
	}
}

func IsAddCommand(command string) bool {
	switch command {
	case "add":
		return true
	default:
		return false
	}
}

func IsGetCommand(command string) bool {
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

func IsDeleteCommand(command string) bool {
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

func IsReplaceCommand(command string) bool {
	switch command {
	case "replace":
		return true
	default:
		return false
	}
}

func IsHelpCommand(command string) bool {
	switch command {
	case "help":
		return true
	default:
		return false
	}
}

func HandleError(context string, err error) {
	if err == nil {
		return
	}

	if context == "" {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "%s", err.Error())
	} else {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "%s: %s", context, err.Error())
	}

	os.Exit(1)
}

func ValidateRegexes(argList ...any) error {
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

func FormatTime(ts fnd.Timestamp) string {
	return ts.Time().Format("02 Jan 2006 15:04 MST")
}
