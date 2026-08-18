package command

import (
	"fmt"

	"ucloud.dk/ucloud_cli/pkg/shared"
	"ucloud.dk/ucloud_cli/pkg/tui"
)

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
	cfg, err := shared.ReadConfig()
	if err != nil {
		return err
	}

	for key, workspace := range cfg.Workspaces {
		fmt.Printf("%-15s  %s\n", key, workspace.Title)
	}

	return nil
}

func (c WorkspaceUseCommand) Execute() error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		return err
	}
	elems := make([]string, 0)
	for key, val := range cfg.Workspaces {
		elem := fmt.Sprintf("%s (%s)", key, val.Title)
		elems = append(elems, elem)
	}
	model := tui.ListModel{
		Items:    elems,
		Selected: 0,
	}
	tui.List(&model)
	fmt.Println("Selected workspace:", model.Selected)
	return nil
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
