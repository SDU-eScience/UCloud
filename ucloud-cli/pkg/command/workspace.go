package command

import "fmt"

type WorkspaceListCommand struct{}
type WorkspaceUseCommand struct {
	Name string `positional:"name" usage:"Workspace name"`
}
type WorkspaceGetCommand struct {
	Name string `positional:"name" usage:"Workspace name"`
}
type WorkspaceDeleteCommand struct {
	Name string `positional:"name" usage:"Workspace name"`
}
type WorkspaceRenameCommand struct {
	FromName string `positional:"from" usage:"Workspace name" required:"true"`
	ToName   string `positional:"to" usage:"Workspace name" required:"true"`
}

var WorkspaceCommands = map[string]CommandFunc{
	"list":   func() Command { return &WorkspaceListCommand{} },
	"use":    func() Command { return &WorkspaceUseCommand{} },
	"get":    func() Command { return &WorkspaceGetCommand{} },
	"delete": func() Command { return &WorkspaceDeleteCommand{} },
	"rename": func() Command { return &WorkspaceRenameCommand{} },
}

func (c WorkspaceListCommand) Execute() error {
	return fmt.Errorf("workspace list not implemented")
}

func (c WorkspaceUseCommand) Execute() error {
	return fmt.Errorf("workspace use not implemented")
}

func (c WorkspaceGetCommand) Execute() error {
	return fmt.Errorf("workspace get not implemented")
}

func (c WorkspaceDeleteCommand) Execute() error {
	return fmt.Errorf("workspace delete not implemented")
}

func (c WorkspaceRenameCommand) Execute() error {
	return fmt.Errorf("workspace rename not implemented")
}
