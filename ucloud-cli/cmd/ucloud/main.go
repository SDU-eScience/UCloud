package main

import (
	"fmt"
	"os"
	"strings"

	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/ucloud_cli/pkg/command"
	cli "ucloud.dk/ucloud_cli/pkg/ucloud_cli"
)

func main() {
	args, err := applyColorMode(os.Args[1:])
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	if path, ok := command.HelpPath(args); ok {
		if err := command.ShowHelp(path); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		return
	}

	if len(args) > 0 {
		command.AutoUpdate(args[0])
	}

	err = cli.ExecuteCommand(append([]string{os.Args[0]}, args...)...)
	if err != nil {
		fmt.Fprintln(os.Stderr, termio.Danger("%s", err).Render(termio.ColorEnabled(os.Stderr)))
		os.Exit(1)
	}
}

func applyColorMode(args []string) ([]string, error) {
	result := make([]string, 0, len(args))

	for i := 0; i < len(args); i++ {
		arg := args[i]

		if arg == "--color" {
			if i+1 >= len(args) {
				return nil, fmt.Errorf("--color needs a value: auto, always or never")
			}

			mode, err := termio.ColorModeFromString(args[i+1])
			if err != nil {
				return nil, err
			}
			termio.SetColorMode(mode)
			i++
			continue
		}

		if strings.HasPrefix(arg, "--color=") {
			mode, err := termio.ColorModeFromString(strings.TrimPrefix(arg, "--color="))
			if err != nil {
				return nil, err
			}
			termio.SetColorMode(mode)
			continue
		}

		result = append(result, arg)
	}

	return result, nil
}
