package shared

import (
	"errors"
	"fmt"

	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func SetActiveWorkspace(projectId string) {
	rpc.DefaultClient.ProjectId = util.OptValue(projectId)
}

// SetOrUseDefaultWorkspace set active if name is set else use the stored one
func SetOrUseDefaultWorkspace(cfg *Config, wsName string) (string, error) {
	found := ""
	if wsName != "" {
		ws, err := FindWorkspaceByName(wsName)
		if err != nil {
			return "", err
		}
		if ws != nil {
			found = ws.Name
			SetActiveWorkspace(ws.Id)
		}
	} else {
		getWs, err := GetActiveWorkspace(cfg)
		if err != nil {
			return found, err
		}
		ws, err := FindWorkspaceByName(getWs)
		if err != nil {
			return found, err
		}
		SetActiveWorkspace(ws.Id)
		found = ws.Name
	}
	return found, nil
}

func FindWorkspaceByName(name string) (*Workspace, error) {
	workspaces, err := RetrieveWorkspaces()
	if err != nil {
		return nil, err
	}
	proj, ok := workspaces[name]
	if !ok {
		return nil, errors.New(fmt.Sprintf("Workspace %s does not exist", name))
	}
	return &Workspace{
		Id:   proj.Id,
		Name: name,
	}, nil
}

func RetrieveWorkspaces() (map[string]fndapi.Project, error) {
	result, httpErr := fndapi.ProjectBrowse.Invoke(fndapi.ProjectBrowseRequest{})
	if httpErr.AsError() != nil {
		return map[string]fndapi.Project{}, fmt.Errorf("failed to list workspaces: %s", httpErr.Why)
	}
	workspaces := make(map[string]fndapi.Project)
	for _, workspace := range result.Items {
		repoName := RepositoryProjectName(workspace.Specification.Title)
		workspaces[repoName] = workspace

	}
	return workspaces, nil
}

func FindWorkspaceById(id string) (*Workspace, error) {
	workspaces, err := RetrieveWorkspaces()
	if err != nil {
		return nil, err
	}
	for name, v := range workspaces {
		if v.Id == id {
			return &Workspace{
				Id:   v.Id,
				Name: name,
			}, nil
		}
	}
	return nil, errors.New(fmt.Sprintf("Workspace %s does not exist", id))
}
