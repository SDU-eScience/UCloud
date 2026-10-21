package main

import (
	"fmt"
	"os"

	"ucloud.dk/ucloud_cli/pkg/command"
	cli "ucloud.dk/ucloud_cli/pkg/ucloud_cli"
)

func main() {
	if path, ok := command.HelpPath(os.Args[1:]); ok {
		if err := command.ShowHelp(path); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		return
	}
	command.AutoUpdate(os.Args[1])
	err := cli.ExecuteCommand(os.Args[0:]...) // Command execution
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
