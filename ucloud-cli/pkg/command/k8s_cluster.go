package command

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"os"
	"sort"
	"strings"
	"time"

	"ucloud.dk/shared/pkg/cli"
	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/iapp"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

const k8sConnectTimeout = 3 * time.Minute

var K8sClusterCommands = map[string]CommandFunc{
	"releases":   func() Command { return &K8sClusterReleasesCommand{} },
	"create":     func() Command { return &K8sClusterCreateCommand{} },
	"list":       func() Command { return &K8sClusterListCommand{} },
	"status":     func() Command { return &K8sClusterStatusCommand{} },
	"kubeconfig": func() Command { return &K8sClusterKubeconfigCommand{} },
}

type K8sClusterReleasesCommand struct {
	Workspace string `flag:"workspace" usage:"Workspace to use"`
	Output    string `flag:"output" usage:"Output format: table or json"`
}

type K8sClusterListCommand struct {
	Workspace string `flag:"workspace" usage:"Workspace to use"`
	Output    string `flag:"output" usage:"Output format: table or json"`
}

type K8sClusterStatusCommand struct {
	Name      string `positional:"name" usage:"Cluster name" required:"true"`
	Workspace string `flag:"workspace" usage:"Workspace to use"`
	Output    string `flag:"output" usage:"Output format: table or json"`
}

type K8sClusterKubeconfigCommand struct {
	Name      string `positional:"name" usage:"Cluster name" required:"true"`
	Workspace string `flag:"workspace" usage:"Workspace to use"`
}

type k8sClusterListEntry struct {
	Name      string `json:"name"`
	CreatedAt string `json:"createdAt"`
}

type k8sStatusOutput struct {
	k8sapi.StatusResponse
	DashboardUrl string `json:"dashboardUrl"`
}

func (c *K8sClusterReleasesCommand) Execute() error {
	cfg, err := k8sInitialize(c.Workspace)
	if err != nil {
		return err
	}
	output, err := k8sOutputFormat(c.Output, cfg)
	if err != nil {
		return err
	}

	ctx, cancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer cancel()

	client, err := k8sConnectApp(ctx, util.OptNone[string]())
	if err != nil {
		return err
	}
	defer client.Close()

	response, err := iapp.Call(ctx, client, k8sapi.Releases, util.Empty{})
	if err != nil {
		return fmt.Errorf("could not list the Kubernetes releases: %w", err)
	}

	return k8sPrintReleases(response, output)
}

func (c *K8sClusterListCommand) Execute() error {
	cfg, err := k8sInitialize(c.Workspace)
	if err != nil {
		return err
	}
	output, err := k8sOutputFormat(c.Output, cfg)
	if err != nil {
		return err
	}

	stacks, err := k8sBrowseClusters()
	if err != nil {
		return err
	}

	return k8sPrintClusters(stacks, output)
}

func (c *K8sClusterStatusCommand) Execute() error {
	if strings.TrimSpace(c.Name) == "" {
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

	stack, err := k8sRetrieveCluster(c.Name)
	if err != nil {
		return err
	}

	ctx, cancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer cancel()

	client, err := k8sConnectCluster(ctx, stack)
	if err != nil {
		return err
	}
	defer client.Close()

	response, err := iapp.Call(ctx, client, k8sapi.Status, util.Empty{})
	if err != nil {
		return fmt.Errorf("could not read the status of %s: %w", c.Name, err)
	}

	return k8sPrintStatus(response, output)
}

func (c *K8sClusterKubeconfigCommand) Execute() error {
	if strings.TrimSpace(c.Name) == "" {
		return fmt.Errorf("a cluster name is required")
	}

	if _, err := k8sInitialize(c.Workspace); err != nil {
		return err
	}

	stack, err := k8sRetrieveCluster(c.Name)
	if err != nil {
		return err
	}

	ctx, cancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer cancel()

	client, err := k8sConnectCluster(ctx, stack)
	if err != nil {
		return err
	}
	defer client.Close()

	response, err := iapp.Call(ctx, client, k8sapi.Kubeconfig, util.Empty{})
	if err != nil {
		return fmt.Errorf("could not read the kubeconfig of %s: %w", c.Name, err)
	}
	if response.Content == "" {
		return fmt.Errorf("the cluster returned an empty kubeconfig")
	}

	fmt.Fprintf(os.Stderr, "Cluster: %s\n", c.Name)
	fmt.Fprintf(os.Stderr, "Dashboard: %s\n", k8sValueOrDash(k8sDashboardURL(c.Name)))
	fmt.Print(response.Content)
	if !strings.HasSuffix(response.Content, "\n") {
		fmt.Println()
	}
	return nil
}

func k8sInitialize(workspace string) (*shared.Config, error) {
	cfg := shared.InitializeUCloudClient()
	if _, err := shared.SetOrUseDefaultWorkspace(cfg, workspace); err != nil {
		return nil, err
	}
	return cfg, nil
}

func k8sOutputFormat(explicit string, cfg *shared.Config) (string, error) {
	value := strings.TrimSpace(explicit)
	if value == "" && cfg != nil {
		value = strings.TrimSpace(cfg.Defaults.Output)
	}

	switch value {
	case "", "table":
		return "table", nil
	case "json":
		return "json", nil
	default:
		return "", fmt.Errorf("unsupported output format %q; use table or json", value)
	}
}

func k8sConnectApp(ctx context.Context, provider util.Option[string]) (*iapp.Client, error) {
	client, err := iapp.ConnectAppWithProvider(ctx, k8sapi.AppName, util.OptNone[string](), provider)
	if err != nil {
		if provider.Present {
			return nil, fmt.Errorf(
				"could not connect to the Kubernetes service in provider %s; check that the provider is available for this workspace: %w",
				provider.Value,
				err,
			)
		}
		return nil, err
	}

	if err := k8sVerifyApp(ctx, client, k8sapi.AppModeCreator, ""); err != nil {
		client.Close()
		return nil, err
	}
	return client, nil
}

func k8sConnectCluster(ctx context.Context, stack orcapi.Stack) (*iapp.Client, error) {
	client, err := iapp.ConnectStack(ctx, stack)
	if err != nil {
		return nil, err
	}

	if err := k8sVerifyApp(ctx, client, k8sapi.AppModeDashboard, stack.Id); err != nil {
		client.Close()
		return nil, err
	}
	return client, nil
}

func k8sVerifyApp(ctx context.Context, client *iapp.Client, expectedMode string, name string) error {
	response, err := iapp.Call(ctx, client, k8sapi.Ping, k8sapi.PingRequest{})
	if err != nil {
		var rpcErr ucx.RpcCallError
		if errors.As(err, &rpcErr) && rpcErr.Status == ucx.RpcStatusNotFound {
			return fmt.Errorf("the Kubernetes application does not support this command; update the application")
		}
		return fmt.Errorf("could not contact the Kubernetes application: %w", err)
	}

	switch response.Mode {
	case "":
		return nil
	case k8sapi.AppModeRecovery:
		if name != "" {
			return fmt.Errorf("the cluster is recovering; use the dashboard at %s", k8sValueOrDash(k8sDashboardURL(name)))
		}
		return fmt.Errorf("the Kubernetes application is in recovery mode; try again later")
	}

	if response.Mode != expectedMode {
		return fmt.Errorf("the Kubernetes application is in %s mode; try again later", response.Mode)
	}
	return nil
}

func k8sRetrieveCluster(name string) (orcapi.Stack, error) {
	stack, err := iapp.RetrieveCluster(name)
	if err != nil {
		return orcapi.Stack{}, err
	}
	if stack.Type != k8sapi.StackType {
		return orcapi.Stack{}, fmt.Errorf("%s is not a Kubernetes cluster", name)
	}
	return stack, nil
}

func k8sBrowseClusters() ([]orcapi.Stack, error) {
	var result []orcapi.Stack
	next := util.OptNone[string]()

	for {
		page, httpErr := orcapi.StacksBrowse.Invoke(orcapi.StacksBrowseRequest{
			ItemsPerPage: 100,
			Next:         next,
		})
		if httpErr != nil {
			return nil, fmt.Errorf("could not list the clusters: %s", httpErr.Why)
		}

		for _, stack := range page.Items {
			if stack.Type == k8sapi.StackType {
				result = append(result, stack)
			}
		}

		if !page.Next.Present {
			return result, nil
		}
		next = page.Next
	}
}

func k8sDashboardURL(name string) string {
	base := ""
	if rpc.DefaultClient != nil {
		base = strings.TrimSuffix(strings.TrimSpace(rpc.DefaultClient.BasePath), "/")
	}
	if base == "" {
		return ""
	}
	return base + "/app/stacks/" + url.PathEscape(name)
}

func k8sPrintReleases(response k8sapi.ReleasesResponse, output string) error {
	if output == "json" {
		return k8sPrintJson(response)
	}

	table := termio.Table{}
	table.AppendHeader("Version")
	table.AppendHeader("Supported until")
	table.AppendHeader("Default")

	for _, release := range response.Releases {
		supportedUntil := "-"
		if release.EolAt > 0 {
			supportedUntil = time.Unix(release.EolAt, 0).UTC().Format("2006-01-02")
		}

		defaultRelease := ""
		if release.Default {
			defaultRelease = "yes"
		}

		table.Cell("%s", release.Version)
		table.Cell("%s", supportedUntil)
		table.Cell("%s", defaultRelease)
	}
	table.Print()
	return nil
}

func k8sPrintClusters(stacks []orcapi.Stack, output string) error {
	sort.Slice(stacks, func(i, j int) bool {
		return stacks[i].Id < stacks[j].Id
	})

	if output == "json" {
		entries := make([]k8sClusterListEntry, 0, len(stacks))
		for _, stack := range stacks {
			entries = append(entries, k8sClusterListEntry{
				Name:      stack.Id,
				CreatedAt: stack.CreatedAt.Time().UTC().Format(time.RFC3339),
			})
		}
		return k8sPrintJson(entries)
	}

	table := termio.Table{}
	table.AppendHeader("Name")
	table.AppendHeader("Created")
	for _, stack := range stacks {
		table.Cell("%s", stack.Id)
		table.Cell("%s", cli.FormatTime(stack.CreatedAt))
	}
	table.Print()
	return nil
}

func k8sPrintStatus(response k8sapi.StatusResponse, output string) error {
	dashboardUrl := k8sDashboardURL(response.Name)

	if output == "json" {
		return k8sPrintJson(k8sStatusOutput{
			StatusResponse: response,
			DashboardUrl:   dashboardUrl,
		})
	}

	var controlPlane *k8sapi.StatusPool
	workerPools := make([]k8sapi.StatusPool, 0, len(response.Pools))
	for i := range response.Pools {
		if response.Pools[i].ControlPlane {
			controlPlane = &response.Pools[i]
		} else {
			workerPools = append(workerPools, response.Pools[i])
		}
	}

	summary := termio.Table{}
	summary.AppendHeader("Field")
	summary.AppendHeader("Value")
	summary.Cell("Cluster")
	summary.Cell("%s", k8sValueOrDash(response.Name))
	summary.Cell("Dashboard")
	summary.Cell("%s", k8sValueOrDash(dashboardUrl))
	summary.Cell("Phase")
	summary.Cell("%s", k8sValueOrDash(response.Phase))
	summary.Cell("Kubernetes")
	summary.Cell("%s", k8sValueOrDash(response.K8sVersion))
	summary.Cell("Provider")
	summary.Cell("%s", k8sValueOrDash(response.MachineProvider))
	if controlPlane != nil {
		summary.Cell("Control plane")
		summary.Cell(
			"%s (%d node%s, %d GB disk)",
			k8sValueOrDash(controlPlane.Machine.Id),
			controlPlane.Nodes,
			k8sCreatePlural(controlPlane.Nodes),
			controlPlane.Disk,
		)
	}
	summary.Cell("Nodes")
	if response.NodeHealthKnown {
		summary.Cell(
			"%d (%d ready, %d not ready, %d unknown)",
			response.NodeReady+response.NodeNotReady+response.NodeUnknown,
			response.NodeReady,
			response.NodeNotReady,
			response.NodeUnknown,
		)
	} else {
		summary.Cell("%d", response.NodeTotal)
	}
	summary.Cell("Node health")
	if response.NodeHealthKnown {
		summary.Cell("available")
	} else {
		summary.Cell("unavailable")
	}
	summary.Cell("Maintenance")
	if !response.MaintenanceKnown {
		if response.MaintenanceError != "" {
			summary.Cell("unavailable (%s)", response.MaintenanceError)
		} else {
			summary.Cell("unavailable")
		}
	} else if len(response.Maintenance) == 0 {
		summary.Cell("none")
	} else {
		summary.Cell("%d", len(response.Maintenance))
	}
	if response.ControlPlaneVersion != "" {
		summary.Cell("Control plane version")
		summary.Cell("%s", response.ControlPlaneVersion)
	}
	if response.FailureReason != "" {
		summary.Cell("Failure")
		summary.Cell("%s", response.FailureReason)
	}
	summary.Print()

	if len(workerPools) > 0 {
		pools := termio.Table{}
		pools.AppendHeader("Worker pool")
		pools.AppendHeader("Machine")
		pools.AppendHeader("Nodes")
		pools.AppendHeader("Disk (GB)")
		for _, pool := range workerPools {
			pools.Cell("%s", pool.Name)
			pools.Cell("%s", k8sValueOrDash(pool.Machine.Id))
			pools.Cell("%d", pool.Nodes)
			pools.Cell("%d", pool.Disk)
		}
		pools.Print()
	} else {
		fmt.Println("Worker pools: none")
	}

	if len(response.Nodes) > 0 {
		nodes := termio.Table{}
		nodes.AppendHeader("Node")
		nodes.AppendHeader("Group")
		nodes.AppendHeader("IP")
		nodes.AppendHeader("Machine")
		nodes.AppendHeader("Ready")
		nodes.AppendHeader("Version")
		for _, node := range response.Nodes {
			nodes.Cell("%s", k8sValueOrDash(node.Hostname))
			nodes.Cell("%s", k8sValueOrDash(node.Group))
			nodes.Cell("%s", k8sValueOrDash(node.IpAddress))
			nodes.Cell("%s", k8sValueOrDash(node.Machine.Id))
			nodes.Cell("%s", k8sReadyLabel(node))
			nodes.Cell("%s", k8sValueOrDash(node.Version))
		}
		nodes.Print()
	}

	if len(response.Maintenance) > 0 {
		operations := termio.Table{}
		operations.AppendHeader("Node")
		operations.AppendHeader("Operation")
		operations.AppendHeader("Phase")
		operations.AppendHeader("Error")
		for _, operation := range response.Maintenance {
			operations.Cell("%s", operation.NodeName)
			operations.Cell("%s", operation.Kind)
			operations.Cell("%s", operation.Phase)
			operations.Cell("%s", k8sValueOrDash(operation.Error))
		}
		operations.Print()
	}

	return nil
}

func k8sPrintJson(value any) error {
	data, err := json.MarshalIndent(value, "", "  ")
	if err != nil {
		return fmt.Errorf("could not encode the output: %w", err)
	}
	fmt.Println(string(data))
	return nil
}

func k8sErrorMessage(err error) string {
	var rpcErr ucx.RpcCallError
	if errors.As(err, &rpcErr) {
		if message := ucx.ValueAsString(rpcErr.Payload["error"]); message != "" {
			return message
		}
	}
	return err.Error()
}

func k8sClusterMessage(message string) string {
	message = strings.ReplaceAll(message, "An application stack", "A cluster")
	message = strings.ReplaceAll(message, "an application stack", "a cluster")
	message = strings.ReplaceAll(message, "application stack", "cluster")
	return message
}

func k8sValueOrDash(value string) string {
	if strings.TrimSpace(value) == "" {
		return "-"
	}
	return value
}

func k8sReadyLabel(node k8sapi.StatusNode) string {
	switch node.State {
	case k8sapi.NodeStateReady:
		return "yes"
	case k8sapi.NodeStateNotReady:
		return "no"
	case k8sapi.NodeStateUnknown:
		return "unknown"
	default:
		return "-"
	}
}
