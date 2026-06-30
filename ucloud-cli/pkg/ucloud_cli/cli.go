package ucloud_cli

import "fmt"

func ExecuteCommand(commands ...string) error {

	command, err := Parse(commands[1:])
	if err != nil {
		return err
	}
	return fmt.Errorf("not implemented %s", command)
}
