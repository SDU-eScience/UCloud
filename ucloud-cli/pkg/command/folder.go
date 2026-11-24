package command

import (
	"encoding/base64"
	"fmt"

	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

type FolderShellCommand struct {
	Path     string `positional:"path" usage:"Folder path" required:"true"`
	Provider string `flag:"provider" usage:"Provider name"`
}

type FolderCreateCommand struct {
	Path string `positional:"path" usage:"Folder path" required:"true"`
}

var FolderCommands = map[string]CommandFunc{
	"shell":  func() Command { return &FolderShellCommand{} },
	"create": func() Command { return &FolderCreateCommand{} },
}

func (c *FolderCreateCommand) Execute() error {
	shared.InitializeUCloudClient()
	_, httpErr := orcapi.FilesCreateFolder.Invoke(fnd.BulkRequest[orcapi.FilesCreateFolderRequest]{
		Items: []orcapi.FilesCreateFolderRequest{
			{
				Id: c.Path,
			},
		},
	})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to create folder: %s", httpErr.Why)
	}
	fmt.Printf("Folder %s created successfully\n", c.Path)
	return nil
}

func (c *FolderShellCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	res, httpErr := orcapi.JobsOpenTerminalInFolder.Invoke(fnd.BulkRequest[orcapi.JobsOpenTerminalInFolderRequestItem]{
		Items: []orcapi.JobsOpenTerminalInFolderRequestItem{
			{
				Folder: c.Path,
			},
		},
	})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to open terminal in folder: %s", httpErr.Why)
	}
	origin := cfg.Environments[cfg.DefaultEnvironment].URL
	usernameHint := base64.StdEncoding.EncodeToString([]byte(cfg.Username))
	err := shared.OpenWebsocketShellSession(new(res.Responses[0]), usernameHint, origin, c.Provider)
	if err != nil {
		return fmt.Errorf("failed to open terminal in folder: %s", err)
	}
	return nil
}
