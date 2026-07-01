package command

import "fmt"

type WorkspaceListCommand struct{}

func (c WorkspaceListCommand) Execute() error {
	return fmt.Errorf("workspace list not implemented")
}

type WorkspaceUseCommand struct {
	Name string
}

func (c WorkspaceUseCommand) Execute() error {
	return fmt.Errorf("workspace use not implemented")
}

type WorkspaceGetCommand struct {
	Name string
}

func (c WorkspaceGetCommand) Execute() error {
	return fmt.Errorf("workspace get not implemented")
}

type WorkspaceDeleteCommand struct {
	Name string
}

func (c WorkspaceDeleteCommand) Execute() error {
	return fmt.Errorf("workspace delete not implemented")
}

type WorkspaceRenameCommand struct {
	FromName string
	ToName   string
}

func (c WorkspaceRenameCommand) Execute() error {
	return fmt.Errorf("workspace rename not implemented")
}
