package command

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"strings"

	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
)

type K8sClusterDeleteCommand struct {
	Name           string `positional:"name" usage:"Cluster name" required:"true"`
	KeepStateDrive bool   `flag:"keep-state-drive" usage:"Keep the state drive and its files"`
	Yes            bool   `flag:"yes" short:"y" usage:"Skip the deletion confirmation"`
	Workspace      string `flag:"workspace" usage:"Workspace to use"`
	Output         string `flag:"output" usage:"Output format: table or json"`
}

type k8sDeleteResult struct {
	Cluster           string `json:"cluster"`
	DeletionRequested bool   `json:"deletionRequested"`
	DeleteStateDrive  bool   `json:"deleteStateDrive"`
}

func (c *K8sClusterDeleteCommand) ParseArguments(args []string) error {
	parsed, err := ArgsBind(args, c, ArgsBindOptions{
		FlagSetName:  "k8s delete",
		FlagsOnly:    true,
		Interspersed: true,
		ShortFlags:   true,
		ErrorOutput:  io.Discard,
	})
	if err != nil {
		return err
	}
	if len(parsed.Positionals) != 1 {
		return fmt.Errorf("expected one cluster name, got %d arguments", len(parsed.Positionals))
	}
	c.Name = parsed.Positionals[0]
	return nil
}

func (c *K8sClusterDeleteCommand) HelpNotes() []string {
	return []string{
		"The cluster and its state drive are deleted in the background, including backups and persistent data.",
		"Use --keep-state-drive to keep the state drive and its files.",
		"Use -y or --yes to skip the confirmation prompt.",
		"The command does not prompt when standard input is not a terminal.",
	}
}

func (c *K8sClusterDeleteCommand) Execute() error {
	name := strings.TrimSpace(c.Name)
	if name == "" {
		return fmt.Errorf("a cluster name is required")
	}

	_, output, err := k8sInitializeOutput(c.Workspace, c.Output)
	if err != nil {
		return err
	}

	_, err = k8sRetrieveCluster(name)
	if err != nil {
		return err
	}

	deleteStateDrive := !c.KeepStateDrive
	if !c.Yes && isTerminal(os.Stdin) {
		if deleteStateDrive {
			message := fmt.Sprintf("Delete cluster %s and its state drive, including backups and persistent data? [y/N] ", termio.QuoteName(name))
			fmt.Fprint(os.Stderr, outputQuestionText(os.Stderr, message))
		} else {
			message := fmt.Sprintf("Delete cluster %s and keep its state drive? [y/N] ", termio.QuoteName(name))
			fmt.Fprint(os.Stderr, outputQuestionText(os.Stderr, message))
		}

		input, err := bufio.NewReader(os.Stdin).ReadString('\n')
		if err != nil {
			return fmt.Errorf("could not read confirmation: %w", err)
		}
		confirmation := strings.ToLower(strings.TrimSpace(input))
		if confirmation != "y" && confirmation != "yes" {
			return fmt.Errorf("cluster deletion cancelled")
		}
	}

	_, httpErr := orcapi.StacksDelete.Invoke(fnd.BulkRequestOf(orcapi.StacksDeleteRequest{
		Id:               name,
		DeleteStateDrive: deleteStateDrive,
	}))
	if httpErr.AsError() != nil {
		return fmt.Errorf("could not request deletion of %s: %s", termio.QuoteName(name), httpErr.Why)
	}

	if output == "json" {
		return k8sPrintJson(k8sDeleteResult{
			Cluster:           name,
			DeletionRequested: true,
			DeleteStateDrive:  deleteStateDrive,
		})
	}

	message := fmt.Sprintf("Cluster %s deletion requested", termio.QuoteName(name))
	fmt.Fprintln(os.Stdout, outputSuccessText(os.Stdout, message))
	if deleteStateDrive {
		fmt.Println("  State drive: Deletion requested")
		warning := termio.Warning("!").Render(termio.ColorEnabled(os.Stdout))
		fmt.Printf("  %s This removes all cluster backups and persistent data.\n", warning)
	} else {
		fmt.Println("  State drive: Kept")
	}
	dashboardUrl := k8sDashboardURL(name)
	if dashboardUrl == "" {
		fmt.Println("  Dashboard: -")
	} else {
		arrow := termio.StyledWith(0, termio.Cyan, "↗").Render(termio.ColorEnabled(os.Stdout))
		fmt.Printf("  %s Dashboard: %s\n", arrow, dashboardUrl)
	}
	return nil
}
