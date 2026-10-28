package command

import (
	"fmt"
	"strings"

	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
)

type K8sClusterDeleteCommand struct {
	Name             string `positional:"name" usage:"Cluster name" required:"true"`
	DeleteStateDrive bool   `flag:"delete-state-drive" usage:"Also delete the state drive and all backups"`
	Workspace        string `flag:"workspace" usage:"Workspace to use"`
	Output           string `flag:"output" usage:"Output format: table or json"`
}

type k8sDeleteResult struct {
	Cluster           string `json:"cluster"`
	DeletionRequested bool   `json:"deletionRequested"`
	DeleteStateDrive  bool   `json:"deleteStateDrive"`
}

func (c *K8sClusterDeleteCommand) HelpNotes() []string {
	return []string{
		"The cluster is deleted in the background. The state drive is kept by default.",
		"--delete-state-drive also removes the state drive and every backup on it.",
	}
}

func (c *K8sClusterDeleteCommand) Execute() error {
	name := strings.TrimSpace(c.Name)
	if name == "" {
		return fmt.Errorf("a cluster name is required")
	}

	cfg, err := k8sInitialize(c.Workspace)
	if err != nil {
		return err
	}
	output, err := k8sOutputFormat(c.Output, cfg)
	if err != nil {
		return err
	}

	if _, err := k8sRetrieveCluster(name); err != nil {
		return err
	}

	_, httpErr := orcapi.StacksDelete.Invoke(fnd.BulkRequestOf(orcapi.StacksDeleteRequest{
		Id:               name,
		DeleteStateDrive: c.DeleteStateDrive,
	}))
	if httpErr.AsError() != nil {
		return fmt.Errorf("could not request deletion of %s: %s", name, httpErr.Why)
	}

	if output == "json" {
		return k8sPrintJson(k8sDeleteResult{
			Cluster:           name,
			DeletionRequested: true,
			DeleteStateDrive:  c.DeleteStateDrive,
		})
	}

	k8sPrintResultSpacer()

	frame := termio.Frame{}
	frame.Title("Cluster " + name)
	frame.AppendFieldStyled("Status", termio.Success("Deletion requested"))
	if c.DeleteStateDrive {
		frame.AppendField("State drive", "Deleted")
		frame.AppendFieldStyled("Warning", termio.Warning("This removes all cluster backups."))
	} else {
		frame.AppendField("State drive", "Kept")
	}
	frame.AppendField("Dashboard", k8sValueOrDash(k8sDashboardURL(name)))
	frame.Print()
	return nil
}
