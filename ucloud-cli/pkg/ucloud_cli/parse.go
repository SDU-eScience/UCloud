package ucloud_cli

import (
	"fmt"
	"strings"

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

	tree := com.CommandTree()
	node, ok := tree[commands[0]]
	if !ok {
		return nil, fmt.Errorf("unknown command %s", commands[0])
	}

	consumed := 1
	for consumed < len(commands) {
		child, ok := node.Children[commands[consumed]]
		if !ok {
			break
		}
		node = child
		consumed++
	}

	if node.Factory == nil {
		return nil, fmt.Errorf("missing subcommand for %s", strings.Join(commands[:consumed], " "))
	}

	cmd := node.Factory()
	err := bindCommand(commands[consumed:], cmd)
	if err != nil {
		return nil, err
	}
	return cmd, nil
}
