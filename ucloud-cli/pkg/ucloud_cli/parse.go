package ucloud_cli

import (
	"fmt"

	com "ucloud.dk/ucloud_cli/pkg/command"
)

func bindCommand(args []string, cmd any) error {
	if parser, ok := cmd.(com.ArgumentParser); ok {
		return parser.ParseArguments(args)
	}
	_, err := com.ArgsBind(args, cmd, com.ArgsBindOptions{})
	return err
}

func Parse(commands []string) (com.Command, error) {
	if len(commands) == 0 {
		return nil, fmt.Errorf("No command specified.")
	}

	var subCommand string
	mainCommand := commands[0]
	if len(commands) > 1 {
		subCommand = commands[1] // secondary positional
	}

	commandParsers := com.CommandRegistry()

	parserRoute, ok := commandParsers[mainCommand]

	if !ok {
		return nil, fmt.Errorf("unknown command %s", mainCommand)
	}

	var cmd com.Command
	var createFunc com.CommandFunc

	sliceCount := 1 // Used to remove the found commands
	createFunc, ok = parserRoute[subCommand]
	if !ok {
		// No subcommand is found, then there is only 1 command
		createFunc, ok = parserRoute[mainCommand]
		if !ok {
			return nil, fmt.Errorf("command %s not found", mainCommand)
		}
	} else {
		sliceCount = 2
	}

	commands = commands[sliceCount:]

	cmd = createFunc()

	err := bindCommand(commands, cmd)

	if err != nil {
		return nil, err
	}
	return cmd, nil
}
