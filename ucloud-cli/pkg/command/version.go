package command

import (
	"fmt"

	"ucloud.dk/ucloud_cli/pkg/version"
)

type VersionCommand struct{}

var VersionCommands = map[string]CommandFunc{
	"version": func() Command { return &VersionCommand{} },
}

func (c VersionCommand) Execute() error {
	fmt.Println(version.Version)
	return nil
}
