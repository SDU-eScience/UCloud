package parsing

import "ucloud.dk/ucloud_cli/pkg/command"

type Parser func([]string) (command.Command, error)
