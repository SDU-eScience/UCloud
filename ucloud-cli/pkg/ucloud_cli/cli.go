package ucloud_cli

import (
	"fmt"

	"ucloud.dk/ucloud_cli/pkg/utils"
)

func ExecuteCommand(commands ...string) error {

	// Consume the first command which should be the ucloud name
	commands, _ = utils.Consume(commands)
	command, err := Parse(commands)

	if err != nil {
		return err
	}
	return fmt.Errorf("not implemented %s", command)
}
