package command

import "fmt"

type FolderShellCommand struct {
	Path string `positional:"path" usage:"Folder path"`
}

var FolderCommands = map[string]CommandFunc{
	"shell": func() Command { return &FolderShellCommand{} },
}

func (c *FolderShellCommand) Execute() error {
	return fmt.Errorf("folder shell not implemented")
}
