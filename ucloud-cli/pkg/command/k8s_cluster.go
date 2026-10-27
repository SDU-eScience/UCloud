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
	"scale": func() Command {
		return &K8sClusterScaleCommand{Nodes: -1, Disk: -1}
	},
	"pool": func() Command {
		return &K8sClusterPoolCommand{Nodes: -1, Disk: -1}
	},
	"node": func() Command {
		return &K8sClusterNodeCommand{Timeout: -1}
	},
	"upgrade": func() Command {
		return &K8sClusterUpgradeCommand{Timeout: -1}
	},
	"backup": func() Command {
		return &K8sClusterBackupCommand{}
	},
	"auto": func() Command {
		return &K8sClusterAutoCommand{StartHour: -1, Days: -1}
	},
	"delete": func() Command {
		return &K8sClusterDeleteCommand{}
	},
	"recovery": func() Command {
		return &K8sClusterRecoveryCommand{}
	},
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

func (c *K8sClusterStatusCommand) HelpNotes() []string {
	return []string{
		"When the dashboard is unavailable, prints the cluster name and the core node jobs.",
	}
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

	stackStatus := stack.Status.GetOrDefault(orcapi.StackStatus{})
	if !stackStatus.UcxConnectJobId.Present || strings.TrimSpace(stackStatus.UcxConnectJobId.Value) == "" {
		return k8sPrintCoreStatus(stack, "the cluster dashboard is not running", output)
	}

	ctx, cancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer cancel()

	client, err := k8sConnectCluster(ctx, stack)
	if err != nil {
		return k8sPrintCoreStatus(stack, err.Error(), output)
	}
	defer client.Close()

	response, err := iapp.Call(ctx, client, k8sapi.Status, util.Empty{})
	if err != nil {
		return fmt.Errorf("could not read the status of %s: %w", c.Name, err)
	}

	if response.Name == "" {
		response.Name = c.Name
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

func k8sConnectRecovery(ctx context.Context, stack orcapi.Stack) (*iapp.Client, error) {
	client, err := iapp.ConnectStack(ctx, stack)
	if err != nil {
		return nil, err
	}

	if err := k8sVerifyApp(ctx, client, k8sapi.AppModeRecovery, stack.Id); err != nil {
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
		if expectedMode == k8sapi.AppModeRecovery {
			return nil
		}
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

	providerLabel := k8sStatusProviderLabel(response.MachineProvider)

	sections := termio.TableSet{}
	sections.SetDimBorders(true)

	summary := termio.Frame{}
	summary.Title("Summary")
	summary.SetNaturalWidth(true)
	summary.SetDimBorders(true)

	summary.AppendField("Cluster", k8sValueOrDash(response.Name))
	summary.AppendField("Dashboard", k8sValueOrDash(dashboardUrl))
	summary.AppendFieldStyled("Phase", k8sStatusPhaseCell(response.Phase))
	if response.FailureReason != "" {
		summary.AppendFieldStyled("Failure", termio.Danger("%s", response.FailureReason))
	}
	summary.AppendField("Provider", providerLabel)
	summary.AppendField("Kubernetes version", k8sValueOrDash(response.K8sVersion))
	if controlPlane != nil {
		summary.AppendField("Control plane", fmt.Sprintf(
			"%s (%d node%s, %d GB disk)",
			k8sValueOrDash(controlPlane.Machine.Id),
			controlPlane.Nodes,
			k8sCreatePlural(controlPlane.Nodes),
			controlPlane.Disk,
		))
	}

	summary.AppendSeparator()

	nodesLabel := fmt.Sprintf("%d", response.NodeTotal)
	if response.NodeHealthKnown {
		nodesLabel = fmt.Sprintf(
			"%d (%d ready, %d not ready, %d unknown)",
			response.NodeReady+response.NodeNotReady+response.NodeUnknown,
			response.NodeReady,
			response.NodeNotReady,
			response.NodeUnknown,
		)
	}
	summary.AppendField("Nodes", nodesLabel)
	if !response.NodeHealthKnown {
		summary.AppendFieldStyled("Live node data", termio.Danger("unavailable"))
	}
	if !response.MaintenanceKnown {
		if response.MaintenanceError != "" {
			summary.AppendFieldStyled("Maintenance data", termio.Danger("unavailable (%s)", response.MaintenanceError))
		} else {
			summary.AppendFieldStyled("Maintenance data", termio.Danger("unavailable"))
		}
	}
	summary.AppendFieldStyled("Rolling upgrade", k8sStatusUpgradeCell(response))

	summary.AppendSeparator()

	summary.AppendFieldStyled("Backup state", k8sStatusBackupStateCell(response))
	if response.Backup != nil && (response.Backup.StateFound || response.Backup.RequestFound) {
		summary.AppendFieldStyled("Last backup", k8sStatusLastBackupCell(response.Backup))
	}
	if response.Backup != nil && response.Backup.NextDueAt > 0 {
		summary.AppendField("Next backup", k8sAutoTimestamp(response.Backup.NextDueAt))
	}
	if response.Backup != nil && response.Backup.StateFound {
		summary.AppendField("Retained backups", fmt.Sprintf("%d", response.Backup.Retained))
	}

	summary.Print()
	fmt.Println()

	failures := k8sStatusMaintenanceFailures(response.Maintenance)
	if len(failures) > 0 {
		errorsTable := k8sStatusMaintenanceErrorTable(failures)
		sections.Add("Maintenance errors", &errorsTable)
	}

	pools := termio.Table{}
	pools.AppendHeader("Worker pool")
	pools.AppendHeader("Machine")
	pools.AppendHeaderEx("Nodes", termio.TableHeaderAlignRight)
	pools.AppendHeaderEx("Disk (GB)", termio.TableHeaderAlignRight)
	for _, pool := range workerPools {
		pools.Cell("%s", pool.Name)
		pools.Cell("%s", k8sValueOrDash(pool.Machine.Id))
		pools.Cell("%d", pool.Nodes)
		pools.Cell("%d", pool.Disk)
	}
	sections.Add("Worker pools", &pools)

	nodes, unmatched := k8sStatusNodeTable(response)
	sections.Add(fmt.Sprintf("Nodes (%d)", len(response.Nodes)), &nodes)
	if len(unmatched) > 0 {
		maintenance := k8sStatusMaintenanceTable(unmatched)
		sections.Add(fmt.Sprintf("Maintenance (%d)", len(unmatched)), &maintenance)
	}

	if response.RollingUpgrade != nil && len(response.RollingUpgrade.Nodes) > 0 {
		upgrade := termio.Table{}
		upgrade.AppendHeader("Upgrade node")
		upgrade.AppendHeader("Group")
		upgrade.AppendHeader("Phase")
		upgrade.SetColumnWidthGroup(0, "node")
		for _, node := range response.RollingUpgrade.Nodes {
			upgrade.Cell("%s", node.Name)
			upgrade.Cell("%s", node.Group)
			upgrade.CellValue(k8sStatusPhaseStyle(node.Phase, k8sUpgradePhaseLabel(node.Phase)))
		}
		sections.Add("Rolling upgrade", &upgrade)
	}

	sections.Print()
	return nil
}

func k8sStatusProviderLabel(providerId string) string {
	providerId = strings.TrimSpace(providerId)
	if providerId == "" {
		return "-"
	}

	branding, httpErr := orcapi.ProviderBrandingBrowse.Invoke(util.Empty{})
	if httpErr != nil {
		return providerId
	}

	entry, ok := branding.Providers[providerId]
	if !ok {
		return providerId
	}

	shortTitle := strings.TrimSpace(entry.ShortTitle)
	if shortTitle == "" {
		shortTitle = strings.TrimSpace(entry.Title)
	}
	if shortTitle == "" {
		return providerId
	}

	return fmt.Sprintf("%s (%s)", shortTitle, providerId)
}

func k8sStatusPhaseCell(phase string) termio.StyledString {
	switch phase {
	case k8sapi.ClusterPhaseCreated:
		return termio.Success("%s", k8sValueOrDash(phase))
	case k8sapi.ClusterPhaseError:
		return termio.Danger("%s", k8sValueOrDash(phase))
	case k8sapi.ClusterPhaseProvisioning, k8sapi.ClusterPhaseCleanup, k8sapi.ClusterPhaseRecovering:
		return termio.Warning("%s", k8sValueOrDash(phase))
	default:
		return termio.Plain("%s", k8sValueOrDash(phase))
	}
}

func k8sStatusMaintenanceFailures(operations []k8sapi.MaintenanceOperation) []k8sapi.MaintenanceOperation {
	failures := make([]k8sapi.MaintenanceOperation, 0)
	for _, operation := range operations {
		if operation.Phase == k8sapi.MaintenancePhaseFailed ||
			operation.Phase == k8sapi.MaintenancePhaseBlocked {
			failures = append(failures, operation)
		}
	}
	return failures
}

func k8sStatusMaintenanceErrorTable(operations []k8sapi.MaintenanceOperation) termio.Table {
	table := termio.Table{}
	table.AppendHeader("Node")
	table.AppendHeader("Operation")
	table.AppendHeader("Phase")
	table.AppendHeader("Error")
	table.SetColumnWidthGroup(0, "node")
	table.SetColumnMaxWidth(3, 60)

	for _, operation := range operations {
		table.Cell("%s", k8sValueOrDash(operation.NodeName))
		table.Cell("%s", k8sValueOrDash(operation.Kind))
		table.CellValue(k8sStatusPhaseStyle(operation.Phase, k8sValueOrDash(operation.Phase)))
		if operation.Error != "" {
			table.CellValue(termio.Danger("%s", operation.Error))
		} else {
			table.CellValue(termio.Subtle("-"))
		}
	}

	return table
}

func k8sStatusUpgradeCell(response k8sapi.StatusResponse) termio.StyledString {
	if response.RollingUpgradeError != "" {
		return termio.Danger("unavailable (%s)", response.RollingUpgradeError)
	}

	upgrade := response.RollingUpgrade
	if upgrade == nil {
		return termio.Subtle("none in progress")
	}

	label := fmt.Sprintf(
		"%s (%s)",
		k8sValueOrDash(upgrade.TargetRelease),
		k8sUpgradePhaseLabel(upgrade.Phase),
	)
	if upgrade.Error != "" {
		return termio.Danger("%s: %s", label, upgrade.Error)
	}
	return k8sStatusPhaseStyle(upgrade.Phase, label)
}

func k8sStatusPhaseStyle(phase string, label string) termio.StyledString {
	switch phase {
	case k8sapi.MaintenancePhaseCompleted:
		return termio.Success("%s", label)
	case k8sapi.MaintenancePhaseFailed, k8sapi.MaintenancePhaseBlocked:
		return termio.Danger("%s", label)
	case k8sapi.MaintenancePhaseCancelled:
		return termio.Subtle("%s", label)
	case k8sapi.MaintenancePhasePending, k8sapi.MaintenancePhaseRunning:
		return termio.Warning("%s", label)
	default:
		return termio.Plain("%s", label)
	}
}

func k8sStatusBackupStateCell(response k8sapi.StatusResponse) termio.StyledString {
	if response.BackupError != "" {
		return termio.Danger("unavailable (%s)", response.BackupError)
	}

	backup := response.Backup
	if backup == nil || (!backup.StateFound && !backup.RequestFound) {
		return termio.Subtle("not configured")
	}

	if backup.RequestFound && k8sBackupRequestActive(backup.RequestPhase) {
		return termio.Warning("%s", backup.RequestPhase)
	}

	if backup.StateFound {
		if backup.LastAttemptPhase == k8sapi.BackupAttemptFailed {
			return termio.Danger("failed")
		}
		if backup.LastSuccessFinishedAt > 0 {
			return termio.Success("ok")
		}
		if backup.NextDueAt > 0 {
			return termio.Plain("scheduled")
		}
	}

	if backup.RequestFound && backup.RequestPhase != "" {
		return termio.Plain("%s", backup.RequestPhase)
	}

	return termio.Subtle("never")
}

func k8sStatusLastBackupCell(backup *k8sapi.StatusBackup) termio.StyledString {
	if backup == nil {
		return termio.Subtle("never")
	}

	if backup.LastAttemptPhase == k8sapi.BackupAttemptFailed {
		if backup.LastAttemptFinishedAt > 0 {
			return termio.Danger("%s (failed)", k8sAutoTimestamp(backup.LastAttemptFinishedAt))
		}
		return termio.Danger("failed")
	}

	if backup.LastSuccessFinishedAt > 0 {
		return termio.Plain("%s", k8sAutoTimestamp(backup.LastSuccessFinishedAt))
	}

	return termio.Subtle("never")
}

func k8sStatusNodeTable(response k8sapi.StatusResponse) (termio.Table, []k8sapi.MaintenanceOperation) {
	maintenance := make(map[string]k8sapi.MaintenanceOperation, len(response.Maintenance))
	for _, operation := range response.Maintenance {
		maintenance[operation.NodeName] = operation
	}

	nodeNames := make(map[string]bool, len(response.Nodes))
	for _, node := range response.Nodes {
		nodeNames[node.Hostname] = true
	}

	hasMaintenance := false
	unmatched := make([]k8sapi.MaintenanceOperation, 0)
	for _, operation := range response.Maintenance {
		if !nodeNames[operation.NodeName] {
			if operation.Phase != k8sapi.MaintenancePhaseFailed &&
				operation.Phase != k8sapi.MaintenancePhaseBlocked {
				unmatched = append(unmatched, operation)
			}
		} else {
			hasMaintenance = true
		}
	}

	table := termio.Table{}
	table.AppendHeader("Node")
	table.AppendHeader("Group")
	table.AppendHeader("IP")
	table.AppendHeader("Machine")
	table.AppendHeader("Ready")
	table.AppendHeader("Version")
	if hasMaintenance {
		table.AppendHeader("Maintenance")
		table.SetColumnMaxWidth(6, 48)
	}
	table.SetColumnWidthGroup(0, "node")

	for _, node := range response.Nodes {
		table.Cell("%s", k8sValueOrDash(node.Hostname))
		table.Cell("%s", k8sValueOrDash(node.Group))
		table.Cell("%s", k8sValueOrDash(node.IpAddress))
		table.Cell("%s", k8sValueOrDash(node.Machine.Id))
		table.CellValue(k8sStatusReadyCell(node))
		table.Cell("%s", k8sValueOrDash(node.Version))
		if !hasMaintenance {
			continue
		}
		if operation, ok := maintenance[node.Hostname]; ok {
			table.CellValue(k8sStatusMaintenanceCell(operation))
		} else {
			table.CellValue(termio.Subtle("-"))
		}
	}

	return table, unmatched
}

func k8sStatusReadyCell(node k8sapi.StatusNode) termio.StyledString {
	switch node.State {
	case k8sapi.NodeStateReady:
		return termio.Success("yes")
	case k8sapi.NodeStateNotReady:
		return termio.Danger("no")
	case k8sapi.NodeStateUnknown:
		return termio.Warning("unknown")
	default:
		return termio.Subtle("-")
	}
}

func k8sStatusMaintenanceCell(operation k8sapi.MaintenanceOperation) termio.StyledString {
	label := k8sValueOrDash(operation.Kind)
	if operation.Phase != "" {
		label += " (" + strings.ToLower(operation.Phase) + ")"
	}

	switch operation.Phase {
	case k8sapi.MaintenancePhaseFailed, k8sapi.MaintenancePhaseBlocked:
		return termio.Danger("%s", label)
	case k8sapi.MaintenancePhaseCompleted:
		return termio.Success("%s", label)
	case k8sapi.MaintenancePhaseCancelled:
		return termio.Subtle("%s", label)
	case k8sapi.MaintenancePhasePending, k8sapi.MaintenancePhaseRunning:
		return termio.Warning("%s", label)
	default:
		return termio.Plain("%s", label)
	}
}

func k8sStatusMaintenanceTable(operations []k8sapi.MaintenanceOperation) termio.Table {
	table := termio.Table{}
	table.AppendHeader("Node")
	table.AppendHeader("Operation")
	table.AppendHeader("Phase")
	table.AppendHeader("Error")
	table.SetColumnWidthGroup(0, "node")
	table.SetColumnMaxWidth(3, 48)

	for _, operation := range operations {
		table.Cell("%s", k8sValueOrDash(operation.NodeName))
		table.Cell("%s", k8sValueOrDash(operation.Kind))
		table.CellValue(k8sStatusPhaseStyle(operation.Phase, k8sValueOrDash(operation.Phase)))
		if operation.Error != "" {
			table.CellValue(termio.Danger("%s", operation.Error))
		} else {
			table.CellValue(termio.Subtle("-"))
		}
	}

	return table
}

type k8sCoreStatusJob struct {
	Name  string `json:"name"`
	State string `json:"state"`
}

type k8sCoreStatusOutput struct {
	Name               string             `json:"name"`
	CreatedAt          string             `json:"createdAt"`
	DashboardUrl       string             `json:"dashboardUrl"`
	DashboardAvailable bool               `json:"dashboardAvailable"`
	DashboardReason    string             `json:"dashboardReason,omitempty"`
	Jobs               []k8sCoreStatusJob `json:"jobs"`
}

func k8sPrintCoreStatus(stack orcapi.Stack, reason string, output string) error {
	stackStatus := stack.Status.GetOrDefault(orcapi.StackStatus{})
	jobs := make([]k8sCoreStatusJob, 0, len(stackStatus.Jobs))
	for _, job := range stackStatus.Jobs {
		name := strings.TrimSpace(job.Specification.Name)
		if name == "" {
			name = job.Id
		}
		jobs = append(jobs, k8sCoreStatusJob{
			Name:  name,
			State: string(job.Status.State),
		})
	}

	sort.Slice(jobs, func(i, j int) bool {
		return jobs[i].Name < jobs[j].Name
	})

	if output == "json" {
		return k8sPrintJson(k8sCoreStatusOutput{
			Name:               stack.Id,
			CreatedAt:          stack.CreatedAt.Time().UTC().Format(time.RFC3339),
			DashboardUrl:       k8sDashboardURL(stack.Id),
			DashboardAvailable: false,
			DashboardReason:    reason,
			Jobs:               jobs,
		})
	}

	fmt.Printf("Cluster: %s\n", stack.Id)
	fmt.Printf("Created: %s\n", cli.FormatTime(stack.CreatedAt))
	fmt.Printf("Dashboard: %s\n", k8sValueOrDash(k8sDashboardURL(stack.Id)))
	fmt.Printf("Dashboard available: no\n")
	fmt.Printf("Reason: %s\n", reason)

	if len(jobs) == 0 {
		fmt.Println("Jobs: none")
		return nil
	}

	table := termio.Table{}
	table.AppendHeader("Node")
	table.AppendHeader("State")
	for _, job := range jobs {
		table.Cell("%s", k8sValueOrDash(job.Name))
		table.Cell("%s", k8sValueOrDash(job.State))
	}
	table.Print()
	return nil
}

func k8sBackupRequestActive(phase string) bool {
	return phase == k8sapi.BackupRequestPending || phase == k8sapi.BackupRequestRunning
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
		if rpcErr.Status == ucx.RpcStatusNotFound {
			return "the Kubernetes application does not support this command; update the application"
		}
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
