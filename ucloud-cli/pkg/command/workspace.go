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

func retrieveWorkspaces() (map[string]fndapi.Project, error) {
	result, httpErr := fndapi.ProjectBrowse.Invoke(fndapi.ProjectBrowseRequest{})
	if httpErr.AsError() != nil {
		return map[string]fndapi.Project{}, fmt.Errorf("failed to list workspaces: %s", httpErr.Why)
	}
	workspaces := make(map[string]fndapi.Project)
	for _, workspace := range result.Items {
		repoName := shared.RepositoryProjectName(workspace.Specification.Title)
		workspaces[repoName] = workspace

	}
	return workspaces, nil
}

func (c WorkspaceListCommand) Execute() error {
	shared.InitializeUCloudClient(c.Dev)
	workspaces, err := retrieveWorkspaces()
	if err != nil {
		return err
	}
	for key := range workspaces {
		fmt.Println(key)
	}
	return nil
}

func (c WorkspaceUseCommand) Execute() error {
	return fmt.Errorf("workspace use not implemented")
}

func (c WorkspaceGetCommand) Execute() error {
	shared.InitializeUCloudClient(c.Dev)
	workspaces, err := retrieveWorkspaces()
	if err != nil {
		return err
	}
	found, ok := workspaces[c.Name]
	if !ok {
		return fmt.Errorf("workspace %s not found", c.Name)
	}
	fmt.Println(found.Specification.Title)
	fmt.Println(found)

	return nil
}

func (c WorkspaceDeleteCommand) Execute() error {
	return fmt.Errorf("workspace delete not implemented")
}

func (c WorkspaceRenameCommand) Execute() error {
	return fmt.Errorf("workspace rename not implemented")
}
