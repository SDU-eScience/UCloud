package command

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/iapp"
)

const k8sNodeWaitTimeout = 60 * time.Minute

type K8sClusterNodeCommand struct {
	Action                  string `positional:"action" usage:"Node action: drain, cordon, uncordon, upgrade, remove or cancel" required:"true"`
	Name                    string `positional:"cluster" usage:"Cluster name" required:"true"`
	NodeName                string `positional:"node" usage:"Node name" required:"true"`
	K8sVersion              string `flag:"k8s-version" usage:"Target Kubernetes release for upgrade"`
	Timeout                 int    `flag:"timeout" usage:"Operation timeout in seconds, 30 to 3600 (default 600)"`
	Cordon                  bool   `flag:"cordon" usage:"Cordon the node before an upgrade"`
	Drain                   bool   `flag:"drain" usage:"Drain the node before an upgrade or removal"`
	DeleteVolatilePods      bool   `flag:"delete-volatile-pods" usage:"Also evict pods with emptyDir data and pods without a controller"`
	BypassDisruptionBudgets bool   `flag:"bypass-disruption-budgets" usage:"Evict pods even when a disruption budget forbids it"`
	Force                   bool   `flag:"force" usage:"Use a zero grace period while draining; requires --bypass-disruption-budgets, and --drain for remove"`
	Wait                    bool   `flag:"wait" usage:"Wait until the operation finishes"`
	Workspace               string `flag:"workspace" usage:"Workspace to use"`
	Output                  string `flag:"output" usage:"Output format: table or json"`
}

type k8sNodeActionResult struct {
	Cluster      string `json:"cluster"`
	Node         string `json:"node"`
	Action       string `json:"action"`
	OperationUid string `json:"operationUid,omitempty"`
	DashboardUrl string `json:"dashboardUrl"`
	Completed    bool   `json:"completed"`
}

func (c *K8sClusterNodeCommand) HelpNotes() []string {
	return []string{
		"The actions are drain, cordon, uncordon, upgrade, remove and cancel.",
		"The drain action always cordons the node before it evicts pods.",
		"Use --k8s-version with the upgrade action.",
		"Use --wait to block until the operation finishes.",
	}
}

func (c *K8sClusterNodeCommand) Execute() (resultErr error) {
	action := strings.ToLower(strings.TrimSpace(c.Action))
	switch action {
	case k8sapi.NodeActionDrain,
		k8sapi.NodeActionCordon,
		k8sapi.NodeActionUncordon,
		k8sapi.NodeActionUpgrade,
		k8sapi.NodeActionRemove,
		k8sapi.NodeActionCancel:
	default:
		return fmt.Errorf(
			"unknown node action %q; use drain, cordon, uncordon, upgrade, remove or cancel",
			c.Action,
		)
	}

	name := strings.TrimSpace(c.Name)
	if name == "" {
		return fmt.Errorf("a cluster name is required")
	}

	node := strings.TrimSpace(c.NodeName)
	if node == "" {
		return fmt.Errorf("a node name is required")
	}

	targetRelease := strings.TrimSpace(c.K8sVersion)
	if action == k8sapi.NodeActionUpgrade && targetRelease == "" {
		return fmt.Errorf("--k8s-version is required for node upgrades")
	}

	timeout := c.Timeout
	if timeout < 0 {
		timeout = k8sapi.MaintenanceTimeoutDefault
	}
	if timeout < k8sapi.MaintenanceTimeoutMin || timeout > k8sapi.MaintenanceTimeoutMax {
		return fmt.Errorf(
			"the timeout must be from %d to %d seconds",
			k8sapi.MaintenanceTimeoutMin,
			k8sapi.MaintenanceTimeoutMax,
		)
	}

	if action == k8sapi.NodeActionUpgrade && c.Force {
		return fmt.Errorf("--force cannot be used with node upgrades")
	}
	if (action == k8sapi.NodeActionDrain || action == k8sapi.NodeActionRemove) &&
		c.Force &&
		!c.BypassDisruptionBudgets {
		return fmt.Errorf("--force requires --bypass-disruption-budgets")
	}
	if action == k8sapi.NodeActionRemove && c.Force && !c.Drain {
		return fmt.Errorf("--force requires --drain")
	}

	_, output, err := k8sInitializeOutput(c.Workspace, c.Output)
	if err != nil {
		return err
	}

	ctx, cancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer cancel()

	client, err := k8sConnectNamedCluster(ctx, name)
	if err != nil {
		return err
	}
	defer client.Close()

	messages := k8sStartMessages(client, output)
	defer func() {
		resultErr = k8sStopMessages(client, messages, resultErr)
	}()

	request := k8sapi.NodeActionRequest{
		Action:                  action,
		Node:                    node,
		TargetRelease:           targetRelease,
		TimeoutSeconds:          timeout,
		Cordon:                  c.Cordon,
		Drain:                   c.Drain,
		DeleteVolatilePods:      c.DeleteVolatilePods,
		BypassDisruptionBudgets: c.BypassDisruptionBudgets,
		ForceDelete:             c.Force,
	}

	switch action {
	case k8sapi.NodeActionDrain:
		request.Cordon = true
		request.Drain = true
	case k8sapi.NodeActionCordon:
		request.Cordon = true
	case k8sapi.NodeActionRemove:
		request.Cordon = c.Drain
	case k8sapi.NodeActionUpgrade:
		if c.Drain {
			request.Cordon = true
		}
	}

	response, err := iapp.Call(ctx, client, k8sapi.NodeAction, request)
	if err != nil {
		return errors.New(k8sClusterMessage(k8sErrorMessage(err)))
	}

	completed := false
	if c.Wait {
		err := k8sWaitForNodeOperation(client, name, response, messages)
		if err != nil {
			return err
		}
		completed = true
	}

	return k8sPrintNodeActionResult(name, response, completed, output)
}

func k8sWaitForNodeOperation(client *iapp.Client, cluster string, result k8sapi.NodeActionResponse, messages *k8sMessageState) error {
	ctx, cancel := context.WithTimeout(context.Background(), k8sNodeWaitTimeout)
	defer cancel()

	client.SetMessageHandler(nil)

	progressOutput := outputProgressWriter(messages.output == "json")
	progress := k8sStartWaitProgress(
		client,
		messages,
		progressOutput,
		fmt.Sprintf("Waiting for %s of node %s", result.Action, termio.QuoteName(result.Node)),
	)
	defer k8sStopWaitProgress(client, progress)

	progress.SetItem(termio.ProgressItem{
		Key:    "node",
		Label:  termio.EscapeName(result.Node),
		Status: "pending",
		State:  termio.ProgressPending,
	})

	lastPhase := ""

	for {
		response, err := iapp.Call(ctx, client, k8sapi.Status, util.Empty{})
		if err != nil {
			if !k8sWaitSleep(ctx) {
				return k8sNodeWaitTimeoutError(result, cluster)
			}
			continue
		}

		if err := k8sClusterFailure(response, cluster); err != nil {
			return err
		}

		operation, found := k8sFindMaintenanceOperation(response, result.Node, result.OperationUid)
		if found {
			if operation.Phase != lastPhase {
				lastPhase = operation.Phase
				progress.SetItem(termio.ProgressItem{
					Key:    "node",
					Label:  termio.EscapeName(result.Node),
					Status: k8sNodeOperationLabel(operation),
					State:  k8sMaintenanceState(operation.Phase),
				})
			}

			if !k8sMaintenanceActive(operation.Phase) {
				if result.Action == k8sapi.NodeActionCancel {
					progress.Summary(outputSuccessText(progressOutput, fmt.Sprintf("Node %s has no active operation", termio.QuoteName(result.Node))))
					return nil
				}

				if operation.Phase == k8sapi.MaintenancePhaseCompleted {
					if result.Action != k8sapi.NodeActionRemove {
						progress.Summary(outputSuccessText(progressOutput, fmt.Sprintf("Node %s %s completed", termio.QuoteName(result.Node), result.Action)))
						return nil
					}
				} else {
					message := operation.Error
					if message == "" {
						message = "the operation ended in phase " + operation.Phase
					}
					return fmt.Errorf("the %s of node %s failed: %s", result.Action, termio.QuoteName(result.Node), message)
				}
			}
		}

		if result.Action == k8sapi.NodeActionRemove && !k8sNodeExists(response.Nodes, result.Node) {
			progress.Summary(outputSuccessText(progressOutput, fmt.Sprintf("Node %s has been removed", termio.QuoteName(result.Node))))
			return nil
		}

		if result.Action == k8sapi.NodeActionCancel && !found {
			progress.Summary(outputSuccessText(progressOutput, fmt.Sprintf("Node %s has no active operation", termio.QuoteName(result.Node))))
			return nil
		}

		if !k8sWaitSleep(ctx) {
			return k8sNodeWaitTimeoutError(result, cluster)
		}
	}
}

func k8sNodeOperationLabel(operation k8sapi.MaintenanceOperation) string {
	label := k8sUpgradePhaseLabel(operation.Phase)
	if operation.Kind == "" {
		return label
	}
	return operation.Kind + " " + label
}

func k8sFindMaintenanceOperation(
	response k8sapi.StatusResponse,
	node string,
	uid string,
) (k8sapi.MaintenanceOperation, bool) {
	for _, operation := range response.Maintenance {
		if operation.NodeName != node {
			continue
		}
		if uid != "" && operation.Uid != uid {
			continue
		}
		return operation, true
	}
	return k8sapi.MaintenanceOperation{}, false
}

func k8sNodeExists(nodes []k8sapi.StatusNode, name string) bool {
	for _, node := range nodes {
		if node.Hostname == name {
			return true
		}
	}
	return false
}

func k8sMaintenanceActive(phase string) bool {
	return phase == k8sapi.MaintenancePhasePending || phase == k8sapi.MaintenancePhaseRunning
}

func k8sNodeWaitTimeoutError(result k8sapi.NodeActionResponse, cluster string) error {
	return fmt.Errorf(
		"the %s of node %s did not finish within %s; use the dashboard at %s",
		result.Action,
		termio.QuoteName(result.Node),
		k8sNodeWaitTimeout,
		k8sValueOrDash(k8sDashboardURL(cluster)),
	)
}

func k8sPrintNodeActionResult(
	cluster string,
	result k8sapi.NodeActionResponse,
	completed bool,
	output string,
) error {
	dashboardUrl := k8sDashboardURL(cluster)

	if output == "json" {
		return k8sPrintJson(k8sNodeActionResult{
			Cluster:      cluster,
			Node:         result.Node,
			Action:       result.Action,
			OperationUid: result.OperationUid,
			DashboardUrl: dashboardUrl,
			Completed:    completed,
		})
	}

	message := fmt.Sprintf("Node %s %s requested", termio.QuoteName(result.Node), result.Action)
	frame := k8sActionResultFrame(cluster, completed, message)
	frame.AppendField("Node", termio.EscapeName(result.Node))
	frame.AppendField("Action", result.Action)
	if result.OperationUid != "" {
		frame.AppendField("Operation", result.OperationUid)
	}
	frame.AppendField("Dashboard", outputLink(dashboardUrl))
	frame.Print()
	return nil
}
