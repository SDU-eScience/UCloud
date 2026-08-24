package command

import (
	"fmt"

	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

type WorkspaceListCommand struct {
	Dev bool `flag:"dev" usage:"Dev mode"`
}
type WorkspaceUseCommand struct {
	Name string `positional:"name" usage:"Workspace name"`
	Dev  bool   `flag:"dev" usage:"Dev mode"`
}
type WorkspaceGetCommand struct {
	Name string `positional:"name" usage:"Workspace name" required:"true"`
	Dev  bool   `flag:"dev" usage:"Dev mode"`
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

	shared.InitializeUCloudClient(c.Dev)
	result, httpErr := fndapi.ProjectBrowse.Invoke(fndapi.ProjectBrowseRequest{})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to list workspaces: %s", httpErr.Why)
	}
	for _, workspace := range result.Items {
		fmt.Println(workspace.Specification.Title)
	}
	return nil
}

func (c WorkspaceUseCommand) Execute() error {
	return fmt.Errorf("workspace use not implemented")
}

func (c WorkspaceGetCommand) Execute() error {
	shared.InitializeUCloudClient(c.Dev)
	proj, httpErr := fndapi.ProjectRetrieve.Invoke(fndapi.ProjectRetrieveRequest{
		Id: c.Name,
	})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to get workspace: %s", httpErr.Why)
	}
	fmt.Println(proj.Specification.Title)
	return nil
}

func (c WorkspaceDeleteCommand) Execute() error {
	return fmt.Errorf("workspace delete not implemented")
}

func (c WorkspaceRenameCommand) Execute() error {
	return fmt.Errorf("workspace rename not implemented")
}
