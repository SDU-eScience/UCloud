package ucloud_cli

import (
	"fmt"

	com "ucloud.dk/ucloud_cli/pkg/command"
	"ucloud.dk/ucloud_cli/pkg/utils"
)

type CommandFunc func() com.Command

// Register a command parser
var commandParsers = map[string]map[string]CommandFunc{
	"compute": {
		"products": func() com.Command {
			return &com.ComputeProductsCommand{}
		},
	},
}

func Parse(args []string) (com.Command, error) {
	if len(args) == 0 {
		return nil, fmt.Errorf("no command")
	}

	args, commandPhrase := utils.Consume(args)
	subCommand := utils.Peek(args)

	parserRoute, ok := commandParsers[commandPhrase]
	if !ok {
		return nil, fmt.Errorf("command %s not found", commandPhrase)
	}

	commandCreator, ok := parserRoute[subCommand]
	if !ok {
		return nil, fmt.Errorf("subcommand %s not found", subCommand)
	}
	cmd := commandCreator()
	args, _ = utils.Consume(args)

	err := com.Bind(args, cmd)
	if err != nil {
		return nil, err
	}
	return cmd, nil
}
