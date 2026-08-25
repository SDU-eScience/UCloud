package command

import (
	"fmt"

	"ucloud.dk/shared/pkg/cli"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

type WorkspaceListCommand struct {
	Dev bool `flag:"dev" usage:"Dev mode"`
}
type WorkspaceUseCommand struct {
	Name string `positional:"name" usage:"Workspace name" required:"true"`
	Dev  bool   `flag:"dev" usage:"Dev mode"`
}
type WorkspaceGetCommand struct {
	Name string `positional:"name" usage:"Workspace name" required:"true"`
	Dev  bool   `flag:"dev" usage:"Dev mode"`
}
type WorkspaceRenameCommand struct {
	FromName string `positional:"from" usage:"Workspace name" required:"true"`
	ToName   string `positional:"to" usage:"Workspace name" required:"true"`
}

var WorkspaceCommands = map[string]CommandFunc{
	"list":   func() Command { return &WorkspaceListCommand{} },
	"use":    func() Command { return &WorkspaceUseCommand{} },
	"get":    func() Command { return &WorkspaceGetCommand{} },
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
	t := termio.Table{}
	t.AppendHeader("Workspaces")
	t.AppendHeader("Id")
	t.AppendHeader("Parent")
	t.AppendHeader("CanConsumeResources")
	t.AppendHeader("CreatedAt")
	for key, v := range workspaces {
		t.Cell("%v", key)
		t.Cell("%v", v.Id)
		t.Cell("%v", v.Specification.Parent.GetOrDefault(""))
		t.Cell("%v", v.Specification.CanConsumeResources)
		t.Cell("%v", cli.FormatTime(v.CreatedAt))
	}
	t.Print()
	return nil
}

func checkIfWorkspaceExists(name string) bool {
	workspaces, err := retrieveWorkspaces()
	if err != nil {
		return false
	}
	_, ok := workspaces[name]
	return ok
}

func checkIfEnviromentExists(name string) bool {
	cfg, err := shared.ReadConfig()
	if err != nil {
		return false
	}
	_, ok := cfg.Environments[name]
	return ok
}

func (c WorkspaceUseCommand) Execute() error {
	shared.InitializeUCloudClient(c.Dev)
	ok := checkIfWorkspaceExists(c.Name)
	if !ok {
		return fmt.Errorf("you don't have this %s workspace", c.Name)
	}

	cfg, err := shared.UpdateConfig(&shared.Config{
		CurrentWorkspace: c.Name,
	})
	if err != nil {
		return err
	}
	fmt.Println("Workspace updated to ", c.Name)
	shared.PrintConfig(cfg)
	return nil

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
	t := termio.Table{}

	t.AppendHeader("Workspace")
	t.AppendHeader("Id")
	t.AppendHeader("Parent")
	t.AppendHeader("CreatedAt")
	t.AppendHeader("CanConsumeResources")
	t.Cell("%v", c.Name)
	t.Cell("%v", found.Id)
	t.Cell("%v", found.Specification.Parent.GetOrDefault(""))
	t.Cell("%v", found.Specification.CanConsumeResources)
	t.Cell("%v", cli.FormatTime(found.CreatedAt))
	t.Print()

	t = termio.Table{}
	if len(found.Status.Members) == 0 {
		fmt.Println("Has members")
		return nil
	}
	fmt.Println("Members:")

	t.AppendHeader("Username")
	t.AppendHeader("Email")
	t.AppendHeader("Role")

	for _, m := range found.Status.Members {
		t.Cell("%v", m.Username)
		t.Cell("%v", m.Email)
		t.Cell("%v", m.Role)
	}

	t.Print()

	return nil
}

func (c WorkspaceRenameCommand) Execute() error {
	return fmt.Errorf("workspace rename not implemented")
}
