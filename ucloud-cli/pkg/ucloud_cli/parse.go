package ucloud_cli

import (
	"fmt"

	"ucloud.dk/ucloud_cli/pkg/command"
)

func parseCommand(args []string) (command.Command, error) {
	return command.ParseCompute(args)
	//return nil, fmt.Errorf("unknown command %s", args[0])
}

func Parse(args []string) (command.Command, error) {
	if len(args) == 0 {
		return nil, fmt.Errorf("no command provided")
	}
	return parseCommand(args)
}
