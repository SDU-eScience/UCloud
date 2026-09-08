package shared

import (
	"fmt"

	"ucloud.dk/ucloud_cli/pkg/command"
)

func HandleError(cmd command.Command, err error) {
	if err != nil {
		fmt.Printf("The command %T failed with error: %v\n", cmd, err)
	}
}
