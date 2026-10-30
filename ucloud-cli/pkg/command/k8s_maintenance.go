package command

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"os"
	"strings"
	"time"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/iapp"
)

const (
	k8sMaintenanceWaitTimeout = 4 * time.Hour
	k8sBackupWaitTimeout      = 90 * time.Minute
)

type k8sMaintenanceRequest struct {
	Action                  string
	Name                    string
	Task                    []string `flag:"task" usage:"Run only: task: update, k8s-upgrade, os-update, cordon, uncordon or remove. Omit to run the tasks enabled in the schedule. Repeat or comma-separate to combine updates."`
	Nodes                   []string `flag:"node" usage:"Run target or cancel/retry selector: node name. Repeat for multiple run targets."`
	Pools                   []string `flag:"pool" usage:"Run only: target node pool. Repeat for multiple pools."`
	All                     bool     `flag:"all" usage:"Run only: target every node"`
	K8sVersion              string   `flag:"k8s-version" usage:"Run only: target Kubernetes release for Kubernetes upgrades"`
	Timeout                 int      `flag:"timeout" usage:"Run only: task timeout in seconds, 30 to 3600 (default 600)"`
	Drain                   bool     `flag:"drain" usage:"Run only: drain the node for a cordon task"`
	DeleteVolatilePods      bool     `flag:"delete-volatile-pods" usage:"Run only: also evict pods with emptyDir data and pods without a controller"`
	BypassDisruptionBudgets bool     `flag:"bypass-disruption-budgets" usage:"Run only: evict pods even when a disruption budget forbids it"`
	Force                   bool     `flag:"force" usage:"Run only: use a zero grace period while draining; requires --bypass-disruption-budgets"`
	MaxParallel             int      `flag:"max-parallel" usage:"Run only: maximum parallel nodes per group (default 1)"`
	RunUid                  string   `flag:"run" usage:"Cancel/retry only: maintenance run uid (defaults to the active update run)"`
	Wait                    bool     `flag:"wait" usage:"Run/cancel/retry/backup only: wait until the action finishes"`
	K8sEnabled              bool     `flag:"k8s-enabled" usage:"Schedule only: include Kubernetes upgrades in scheduled maintenance"`
	OsEnabled               bool     `flag:"os-enabled" usage:"Schedule only: include operating-system updates in scheduled maintenance"`
	Day                     string   `flag:"day" usage:"Schedule only: maintenance day: mon, tue, wed, thu, fri, sat or sun"`
	Hour                    int      `flag:"hour" usage:"Schedule only: maintenance period start hour UTC, 0 to 23"`
	Yes                     bool     `flag:"yes" short:"y" usage:"Skip the maintenance run confirmation"`
	Workspace               string   `flag:"workspace" usage:"Workspace to use"`
	Output                  string   `flag:"output" usage:"Output format: table or json"`

	visited map[string]bool
}

type k8sMaintenanceRunResult struct {
	Cluster      string `json:"cluster"`
	RunUid       string `json:"runUid"`
	DashboardUrl string `json:"dashboardUrl"`
	Waited       bool   `json:"waited"`
	Phase        string `json:"phase,omitempty"`
}

type k8sMaintenanceScheduleResult struct {
	Cluster      string                         `json:"cluster"`
	Policy       k8sapi.MaintenancePolicyInfo   `json:"policy"`
	Schedule     k8sapi.MaintenanceScheduleInfo `json:"schedule"`
	DashboardUrl string                         `json:"dashboardUrl"`
}

type k8sBackupResult struct {
	Cluster      string `json:"cluster"`
	Uid          string `json:"uid"`
	Phase        string `json:"phase"`
	DashboardUrl string `json:"dashboardUrl"`
	Completed    bool   `json:"completed"`
}

type k8sMaintenanceStatusOutput struct {
	k8sapi.MaintenanceStatusResponse
	DashboardUrl string `json:"dashboardUrl"`
}

func (c *k8sMaintenanceRequest) execute() (resultErr error) {
	action := strings.ToLower(strings.TrimSpace(c.Action))
	switch action {
	case "status", "run", "cancel", "retry", "schedule", "backup":
	default:
		return fmt.Errorf(
			"unknown maintenance action %q; use status, run, cancel, retry, schedule or backup",
			c.Action,
		)
	}
	err := c.validateActionFlags(action)
	if err != nil {
		return err
	}

	name := strings.TrimSpace(c.Name)
	if name == "" {
		return fmt.Errorf("a cluster name is required")
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

	switch action {
	case "status":
		return k8sMaintenanceStatus(client, name, output)
	case "run":
		return c.maintenanceRun(client, name, messages, output)
	case "cancel":
		return k8sMaintenanceCancel(client, name, c, messages, output)
	case "retry":
		return k8sMaintenanceRetry(client, name, c, messages, output)
	case "schedule":
		return k8sMaintenanceSchedule(client, name, c, output)
	case "backup":
		return c.backup(client, name, messages, output)
	}
	return nil
}

func (c *k8sMaintenanceRequest) validateActionFlags(action string) error {
	allowed := map[string]bool{
		"workspace": true,
		"output":    true,
	}
	switch action {
	case "status":
	case "run":
		for _, name := range []string{
			"task",
			"node",
			"pool",
			"all",
			"k8s-version",
			"timeout",
			"drain",
			"delete-volatile-pods",
			"bypass-disruption-budgets",
			"force",
			"max-parallel",
			"wait",
			"yes",
			"y",
		} {
			allowed[name] = true
		}
	case "cancel", "retry":
		allowed["run"] = true
		allowed["node"] = true
		allowed["wait"] = true
	case "schedule":
		allowed["k8s-enabled"] = true
		allowed["os-enabled"] = true
		allowed["day"] = true
		allowed["hour"] = true
	case "backup":
		allowed["wait"] = true
	}

	for name := range c.visited {
		if !allowed[name] {
			return fmt.Errorf("--%s is not used by the %s action", name, action)
		}
	}
	return nil
}

func (c *k8sMaintenanceRequest) maintenanceRun(
	client *iapp.Client,
	cluster string,
	messages *k8sMessageState,
	output string,
) error {
	configured := len(c.Task) == 0
	request, err := c.maintenanceRequest()
	if err != nil {
		return err
	}

	if !c.Yes && isTerminal(os.Stdin) {
		message := ""
		if configured {
			status, statusErr := k8sMaintenanceStatusData(client)
			if statusErr != nil {
				return statusErr
			}
			message, err = k8sConfiguredRunConfirmation(status, cluster)
			if err != nil {
				return err
			}
		} else {
			message = k8sMaintenanceRunConfirmation(cluster, request)
		}
		err = k8sConfirmMaintenanceRun(message)
		if err != nil {
			return err
		}
	}

	callCtx, callCancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer callCancel()

	response, err := iapp.Call(callCtx, client, k8sapi.MaintenanceRun, request)
	if err != nil {
		return errors.New(k8sClusterMessage(k8sErrorMessage(err)))
	}

	waited := c.Wait
	phase := ""
	if c.Wait {
		phase, err = k8sWaitForMaintenanceRun(
			client,
			cluster,
			response.Uid,
			messages,
			true,
			fmt.Sprintf("Waiting for maintenance run %s", termio.QuoteName(response.Uid)),
			"Maintenance completed",
		)
		if err != nil {
			return err
		}
	}

	message := fmt.Sprintf("Maintenance run started for cluster %s", termio.QuoteName(cluster))
	switch phase {
	case k8sapi.MaintenanceRunCompleted:
		message = fmt.Sprintf("Maintenance run completed for cluster %s", termio.QuoteName(cluster))
	case k8sapi.MaintenanceRunCancelled:
		message = fmt.Sprintf("Maintenance run cancelled for cluster %s", termio.QuoteName(cluster))
	}
	return k8sPrintMaintenanceRunResult(cluster, response.Uid, waited, phase, output, message)
}

func k8sConfiguredRunConfirmation(
	status k8sapi.MaintenanceStatusResponse,
	cluster string,
) (string, error) {
	tasks := []string{}
	if status.Policy.K8sEnabled {
		tasks = append(tasks, "Kubernetes upgrade")
	}
	if status.Policy.OsEnabled {
		tasks = append(tasks, "operating-system update")
	}
	if len(tasks) == 0 {
		return "", errors.New("no maintenance task is enabled in the scheduled policy; use --task to start a specific task")
	}
	return fmt.Sprintf(
		"Start maintenance on all nodes in cluster %s with %s?",
		termio.QuoteName(cluster),
		strings.Join(tasks, " and "),
	), nil
}

func k8sMaintenanceRunConfirmation(
	cluster string,
	request k8sapi.MaintenanceRunRequest,
) string {
	target := "all nodes"
	switch {
	case len(request.Target.Nodes) == 1:
		target = "node " + termio.QuoteName(request.Target.Nodes[0])
	case len(request.Target.Nodes) > 1:
		target = "nodes " + k8sQuoteList(request.Target.Nodes)
	case len(request.Target.Pools) == 1:
		target = "node pool " + termio.QuoteName(request.Target.Pools[0])
	case len(request.Target.Pools) > 1:
		target = "node pools " + k8sQuoteList(request.Target.Pools)
	}

	hasK8s := false
	hasOs := false
	taskLabels := []string{}
	for _, task := range request.Tasks {
		switch task.Kind {
		case k8sapi.MaintenanceTaskK8sUpgrade:
			hasK8s = true
		case k8sapi.MaintenanceTaskOsUpdate:
			hasOs = true
		case k8sapi.MaintenanceTaskCordon:
			taskLabels = append(taskLabels, "cordon")
		case k8sapi.MaintenanceTaskUncordon:
			taskLabels = append(taskLabels, "uncordon")
		case k8sapi.MaintenanceTaskRemove:
			taskLabels = append(taskLabels, "remove")
		}
	}
	if hasK8s && hasOs {
		taskLabels = append([]string{"Kubernetes and operating-system update"}, taskLabels...)
	} else if hasK8s {
		taskLabels = append([]string{"Kubernetes upgrade"}, taskLabels...)
	} else if hasOs {
		taskLabels = append([]string{"operating-system update"}, taskLabels...)
	}
	if len(taskLabels) == 0 {
		taskLabels = append(taskLabels, "maintenance")
	}

	return fmt.Sprintf(
		"Start maintenance on %s in cluster %s with %s?",
		target,
		termio.QuoteName(cluster),
		strings.Join(taskLabels, ", "),
	)
}

func k8sQuoteList(values []string) string {
	quoted := make([]string, 0, len(values))
	for _, value := range values {
		quoted = append(quoted, termio.QuoteName(value))
	}
	return strings.Join(quoted, ", ")
}

func k8sConfirmMaintenanceRun(message string) error {
	fmt.Fprint(os.Stderr, outputQuestionText(os.Stderr, message+" [y/N] "))
	input, err := bufio.NewReader(os.Stdin).ReadString('\n')
	if err != nil {
		return fmt.Errorf("could not read confirmation: %w", err)
	}
	confirmation := strings.ToLower(strings.TrimSpace(input))
	if confirmation != "y" && confirmation != "yes" {
		return errors.New("maintenance run cancelled")
	}
	return nil
}

func (c *k8sMaintenanceRequest) maintenanceRequest() (k8sapi.MaintenanceRunRequest, error) {
	configured := len(c.Task) == 0
	if configured {
		if len(c.Nodes) > 0 || len(c.Pools) > 0 {
			return k8sapi.MaintenanceRunRequest{}, fmt.Errorf(
				"run without --task uses all nodes; pass --task when targeting specific nodes or pools",
			)
		}
		if strings.TrimSpace(c.K8sVersion) != "" ||
			c.visited["timeout"] ||
			c.visited["max-parallel"] ||
			c.Drain ||
			c.DeleteVolatilePods ||
			c.BypassDisruptionBudgets ||
			c.Force {
			return k8sapi.MaintenanceRunRequest{}, fmt.Errorf(
				"--task is required when using task options such as --k8s-version, --timeout or --drain",
			)
		}
		return k8sapi.MaintenanceRunRequest{RequestedBy: "ucloud-cli"}, nil
	}

	timeout := c.Timeout
	if timeout < 0 {
		timeout = k8sapi.MaintenanceTimeoutDefault
	}
	if timeout == 0 {
		timeout = k8sapi.MaintenanceTimeoutDefault
	}
	if timeout < k8sapi.MaintenanceTimeoutMin || timeout > k8sapi.MaintenanceTimeoutMax {
		return k8sapi.MaintenanceRunRequest{}, fmt.Errorf(
			"the timeout must be from %d to %d seconds",
			k8sapi.MaintenanceTimeoutMin,
			k8sapi.MaintenanceTimeoutMax,
		)
	}

	maxParallel := c.MaxParallel
	if maxParallel <= 0 {
		maxParallel = 1
	}
	if maxParallel > 8 {
		return k8sapi.MaintenanceRunRequest{}, fmt.Errorf("--max-parallel must be from 1 to 8")
	}

	tasks := make([]k8sapi.MaintenanceTaskRequest, 0, len(c.Task))
	taskNames := c.Task
	expandedTaskNames := make([]string, 0, len(taskNames))
	for _, taskName := range taskNames {
		for _, part := range strings.Split(taskName, ",") {
			part = strings.ToLower(strings.TrimSpace(part))
			switch part {
			case "":
			case "update":
				expandedTaskNames = append(
					expandedTaskNames,
					k8sapi.MaintenanceTaskK8sUpgrade,
					k8sapi.MaintenanceTaskOsUpdate,
				)
			default:
				expandedTaskNames = append(expandedTaskNames, part)
			}
		}
	}
	if len(expandedTaskNames) == 0 {
		return k8sapi.MaintenanceRunRequest{}, fmt.Errorf("select at least one maintenance task")
	}
	for _, taskName := range expandedTaskNames {
		taskName = strings.ToLower(strings.TrimSpace(taskName))
		switch taskName {
		case k8sapi.MaintenanceTaskK8sUpgrade:
			if c.Drain {
				return k8sapi.MaintenanceRunRequest{}, fmt.Errorf("Kubernetes upgrades handle draining automatically and cannot use --drain")
			}
			if c.Force {
				return k8sapi.MaintenanceRunRequest{}, fmt.Errorf("--force cannot be used with Kubernetes upgrades")
			}
			tasks = append(tasks, k8sapi.MaintenanceTaskRequest{
				Kind:                    taskName,
				TargetRelease:           strings.TrimSpace(c.K8sVersion),
				TimeoutSeconds:          timeout,
				DeleteVolatilePods:      c.DeleteVolatilePods,
				BypassDisruptionBudgets: c.BypassDisruptionBudgets,
			})
		case k8sapi.MaintenanceTaskOsUpdate:
			if c.Drain {
				return k8sapi.MaintenanceRunRequest{}, fmt.Errorf("operating-system updates always cordon and drain the node")
			}
			if c.Force {
				return k8sapi.MaintenanceRunRequest{}, fmt.Errorf("operating-system updates cannot force deletion")
			}
			tasks = append(tasks, k8sapi.MaintenanceTaskRequest{
				Kind:               taskName,
				TimeoutSeconds:     timeout,
				DeleteVolatilePods: c.DeleteVolatilePods,
			})
		case k8sapi.MaintenanceTaskCordon:
			if c.Force && !c.BypassDisruptionBudgets {
				return k8sapi.MaintenanceRunRequest{}, fmt.Errorf("--force requires --bypass-disruption-budgets")
			}
			tasks = append(tasks, k8sapi.MaintenanceTaskRequest{
				Kind:                    taskName,
				Preparation:             c.maintenancePreparation(),
				TimeoutSeconds:          timeout,
				DeleteVolatilePods:      c.DeleteVolatilePods,
				BypassDisruptionBudgets: c.BypassDisruptionBudgets,
				ForceDelete:             c.Force,
			})
		case k8sapi.MaintenanceTaskUncordon:
			tasks = append(tasks, k8sapi.MaintenanceTaskRequest{
				Kind:           taskName,
				TimeoutSeconds: timeout,
			})
		case k8sapi.MaintenanceTaskRemove:
			if c.Force && !c.BypassDisruptionBudgets {
				return k8sapi.MaintenanceRunRequest{}, fmt.Errorf("--force requires --bypass-disruption-budgets")
			}
			tasks = append(tasks, k8sapi.MaintenanceTaskRequest{
				Kind:                    taskName,
				TimeoutSeconds:          timeout,
				DeleteVolatilePods:      c.DeleteVolatilePods,
				BypassDisruptionBudgets: c.BypassDisruptionBudgets,
				ForceDelete:             c.Force,
			})
		default:
			return k8sapi.MaintenanceRunRequest{}, fmt.Errorf(
				"unknown task %q; use update, k8s-upgrade, os-update, cordon, uncordon or remove",
				taskName,
			)
		}
	}

	hasK8sTask := false
	hasOsTask := false
	for _, task := range tasks {
		switch task.Kind {
		case k8sapi.MaintenanceTaskK8sUpgrade:
			hasK8sTask = true
		case k8sapi.MaintenanceTaskOsUpdate:
			hasOsTask = true
		}
	}
	if hasOsTask && !hasK8sTask && c.BypassDisruptionBudgets {
		return k8sapi.MaintenanceRunRequest{}, fmt.Errorf("operating-system updates cannot bypass disruption budgets")
	}

	target := k8sapi.MaintenanceTarget{
		All:   c.All,
		Pools: k8sTrimList(c.Pools),
		Nodes: k8sTrimList(c.Nodes),
	}
	nodeAction := false
	for _, task := range tasks {
		if task.Kind == k8sapi.MaintenanceTaskCordon ||
			task.Kind == k8sapi.MaintenanceTaskUncordon ||
			task.Kind == k8sapi.MaintenanceTaskRemove {
			nodeAction = true
		}
	}
	if nodeAction && (target.All || len(target.Pools) > 0 || len(target.Nodes) != 1) {
		return k8sapi.MaintenanceRunRequest{}, fmt.Errorf("node actions require exactly one --node and cannot use --all or --pool")
	}
	if nodeAction && len(tasks) != 1 {
		return k8sapi.MaintenanceRunRequest{}, fmt.Errorf("a node action cannot be combined with another task")
	}
	if !nodeAction && !target.All && len(target.Pools) == 0 && len(target.Nodes) == 0 {
		target.All = true
	}

	return k8sapi.MaintenanceRunRequest{
		Tasks:               tasks,
		Target:              target,
		MaxParallelPerGroup: maxParallel,
		RequestedBy:         "ucloud-cli",
	}, nil
}

func (c *k8sMaintenanceRequest) maintenancePreparation() string {
	if c.Drain {
		return k8sapi.MaintenancePreparationDrain
	}
	return k8sapi.MaintenancePreparationNone
}

func (c *k8sMaintenanceRequest) maintenanceRunSelector() (string, string, error) {
	runUid := strings.TrimSpace(c.RunUid)
	nodes := k8sTrimList(c.Nodes)
	if runUid != "" && len(nodes) > 0 {
		return "", "", fmt.Errorf("use either --run or --node, not both")
	}
	if len(nodes) > 1 {
		return "", "", fmt.Errorf("select exactly one --node")
	}
	node := ""
	if len(nodes) == 1 {
		node = nodes[0]
	}
	return runUid, node, nil
}

func (c *k8sMaintenanceRequest) backup(
	client *iapp.Client,
	cluster string,
	messages *k8sMessageState,
	output string,
) error {
	callCtx, callCancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer callCancel()

	response, err := iapp.Call(callCtx, client, k8sapi.MaintenanceBackup, util.Empty{})
	if err != nil {
		return errors.New(k8sClusterMessage(k8sErrorMessage(err)))
	}

	completed := false
	if c.Wait {
		waitErr := k8sWaitForBackup(client, cluster, response.Uid, messages)
		if waitErr != nil {
			return waitErr
		}
		completed = true
	}

	return k8sPrintBackupResult(cluster, response, completed, output)
}

func k8sMaintenanceStatusData(client *iapp.Client) (k8sapi.MaintenanceStatusResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer cancel()

	response, err := iapp.Call(ctx, client, k8sapi.MaintenanceStatus, util.Empty{})
	if err != nil {
		return k8sapi.MaintenanceStatusResponse{}, errors.New(k8sClusterMessage(k8sErrorMessage(err)))
	}
	return response, nil
}

func k8sMaintenanceStatus(client *iapp.Client, cluster string, output string) error {
	response, err := k8sMaintenanceStatusData(client)
	if err != nil {
		return err
	}
	return k8sPrintMaintenanceStatus(cluster, response, output)
}

func k8sMaintenanceCancel(
	client *iapp.Client,
	cluster string,
	c *k8sMaintenanceRequest,
	messages *k8sMessageState,
	output string,
) error {
	runUid, node, selectorErr := c.maintenanceRunSelector()
	if selectorErr != nil {
		return selectorErr
	}

	ctx, cancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer cancel()

	response, err := iapp.Call(ctx, client, k8sapi.MaintenanceCancel, k8sapi.MaintenanceCancelRequest{
		RunUid: runUid,
		Node:   node,
	})
	if err != nil {
		return errors.New(k8sClusterMessage(k8sErrorMessage(err)))
	}

	waited := c.Wait && response.Uid != ""
	phase := ""
	if waited {
		phase, err = k8sWaitForMaintenanceRun(
			client,
			cluster,
			response.Uid,
			messages,
			false,
			fmt.Sprintf("Waiting for maintenance run %s", termio.QuoteName(response.Uid)),
			"Maintenance completed",
		)
		if err != nil {
			return err
		}
	}
	message := fmt.Sprintf("Cancellation requested for cluster %s", termio.QuoteName(cluster))
	switch phase {
	case k8sapi.MaintenanceRunCancelled:
		message = fmt.Sprintf("Maintenance cancelled for cluster %s", termio.QuoteName(cluster))
	case k8sapi.MaintenanceRunCompleted:
		message = fmt.Sprintf("Maintenance run completed before it could be cancelled for cluster %s", termio.QuoteName(cluster))
	}
	return k8sPrintMaintenanceRunResult(cluster, response.Uid, waited, phase, output, message)
}

func k8sMaintenanceRetry(
	client *iapp.Client,
	cluster string,
	c *k8sMaintenanceRequest,
	messages *k8sMessageState,
	output string,
) error {
	runUid, node, selectorErr := c.maintenanceRunSelector()
	if selectorErr != nil {
		return selectorErr
	}

	ctx, cancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer cancel()

	response, err := iapp.Call(ctx, client, k8sapi.MaintenanceRetry, k8sapi.MaintenanceRetryRequest{
		RunUid: runUid,
		Node:   node,
	})
	if err != nil {
		return errors.New(k8sClusterMessage(k8sErrorMessage(err)))
	}

	waited := c.Wait
	phase := ""
	if waited {
		phase, err = k8sWaitForMaintenanceRun(
			client,
			cluster,
			response.Uid,
			messages,
			true,
			fmt.Sprintf("Waiting for maintenance run %s", termio.QuoteName(response.Uid)),
			"Maintenance completed",
		)
		if err != nil {
			return err
		}
	}
	message := fmt.Sprintf("Retry requested for cluster %s", termio.QuoteName(cluster))
	switch phase {
	case k8sapi.MaintenanceRunCompleted:
		message = fmt.Sprintf("Maintenance run completed for cluster %s", termio.QuoteName(cluster))
	case k8sapi.MaintenanceRunCancelled:
		message = fmt.Sprintf("Maintenance run cancelled for cluster %s", termio.QuoteName(cluster))
	}
	return k8sPrintMaintenanceRunResult(cluster, response.Uid, waited, phase, output, message)
}

func k8sMaintenanceSchedule(
	client *iapp.Client,
	cluster string,
	c *k8sMaintenanceRequest,
	output string,
) error {
	request := k8sapi.MaintenancePolicyUpdateRequest{}
	if c.visited["k8s-enabled"] {
		request.K8sEnabled = util.OptValue(c.K8sEnabled)
	}
	if c.visited["os-enabled"] {
		request.OsEnabled = util.OptValue(c.OsEnabled)
	}
	if c.Day != "" {
		request.WindowDay = strings.ToLower(strings.TrimSpace(c.Day))
	}
	if c.visited["hour"] {
		request.WindowStartHour = util.OptValue(c.Hour)
	}
	if !c.visited["k8s-enabled"] &&
		!c.visited["os-enabled"] &&
		request.WindowDay == "" &&
		!c.visited["hour"] {
		return fmt.Errorf("schedule needs at least one policy option")
	}

	response, err := k8sApplyPolicyUpdate(client, request)
	if err != nil {
		return err
	}
	return k8sPrintMaintenanceScheduleResult(cluster, "Schedule updated", response, output)
}

func k8sApplyPolicyUpdate(
	client *iapp.Client,
	request k8sapi.MaintenancePolicyUpdateRequest,
) (k8sapi.MaintenanceStatusResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer cancel()

	response, err := iapp.Call(ctx, client, k8sapi.MaintenancePolicyUpdate, request)
	if err != nil {
		return k8sapi.MaintenanceStatusResponse{}, errors.New(k8sClusterMessage(k8sErrorMessage(err)))
	}
	return response, nil
}

func k8sWaitForMaintenanceRun(
	client *iapp.Client,
	cluster string,
	runUid string,
	messages *k8sMessageState,
	failOnCancelled bool,
	title string,
	completedMessage string,
) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), k8sMaintenanceWaitTimeout)
	defer cancel()

	client.SetMessageHandler(nil)

	progressOutput := outputProgressWriter(messages.output == "json")
	progress := k8sStartWaitProgress(
		client,
		messages,
		progressOutput,
		title,
	)
	defer k8sStopWaitProgress(client, progress)

	states := map[string]string{}
	for {
		response, err := iapp.Call(ctx, client, k8sapi.MaintenanceStatus, util.Empty{})
		if err != nil {
			if !k8sWaitSleep(ctx) {
				return "", k8sMaintenanceWaitTimeoutError(cluster)
			}
			continue
		}

		run, found := k8sFindMaintenanceRun(response, runUid)
		if !found {
			if !k8sWaitSleep(ctx) {
				return "", k8sMaintenanceWaitTimeoutError(cluster)
			}
			continue
		}

		for _, task := range run.Tasks {
			for _, item := range task.Items {
				stateKey := item.Phase
				if item.Stage != "" {
					stateKey += "\x00" + item.Stage
				}
				if states[item.Node] == stateKey {
					continue
				}
				states[item.Node] = stateKey
				status := k8sUpgradePhaseLabel(item.Phase)
				if item.Stage != "" {
					status = item.Stage
				}
				progress.SetItem(termio.ProgressItem{
					Key:    item.Node,
					Label:  fmt.Sprintf("%s (%s)", termio.EscapeName(item.Node), termio.EscapeName(item.Group)),
					Status: status,
					State:  k8sMaintenanceState(item.Phase),
				})
			}
		}

		switch run.Phase {
		case k8sapi.MaintenanceRunCompleted:
			progress.Summary(outputSuccessText(progressOutput, completedMessage))
			return run.Phase, nil
		case k8sapi.MaintenanceRunFailed, k8sapi.MaintenanceRunBlocked:
			reason := run.Error
			if reason == "" {
				reason = "the maintenance run failed"
			}
			return run.Phase, fmt.Errorf(
				"the maintenance run failed: %s; use the dashboard at %s",
				reason,
				k8sValueOrDash(k8sDashboardURL(cluster)),
			)
		case k8sapi.MaintenanceRunCancelled:
			if failOnCancelled {
				return run.Phase, fmt.Errorf("the maintenance run was cancelled")
			}
			progress.Summary(outputSuccessText(progressOutput, "Maintenance cancelled"))
			return run.Phase, nil
		}

		if !k8sWaitSleep(ctx) {
			return "", k8sMaintenanceWaitTimeoutError(cluster)
		}
	}
}

func k8sFindMaintenanceRun(response k8sapi.MaintenanceStatusResponse, uid string) (k8sapi.MaintenanceRunInfo, bool) {
	for _, run := range response.ActiveRuns {
		if run.Uid == uid {
			return run, true
		}
	}
	if response.LastRun != nil && response.LastRun.Uid == uid {
		return *response.LastRun, true
	}
	for _, run := range response.RecentRuns {
		if run.Uid == uid {
			return run, true
		}
	}
	return k8sapi.MaintenanceRunInfo{}, false
}

func k8sPrintMaintenanceRunResult(
	cluster string,
	runUid string,
	waited bool,
	phase string,
	output string,
	message string,
) error {
	dashboardUrl := k8sDashboardURL(cluster)
	if output == "json" {
		return k8sPrintJson(k8sMaintenanceRunResult{
			Cluster:      cluster,
			RunUid:       runUid,
			DashboardUrl: dashboardUrl,
			Waited:       waited,
			Phase:        phase,
		})
	}

	if waited {
		return nil
	}

	frame := k8sSummaryFrame(message)
	frame.AppendField("Run", k8sValueOrDash(runUid))
	frame.AppendField("Dashboard", outputLink(dashboardUrl))
	frame.Print()
	return nil
}

func k8sPrintMaintenanceStatus(
	cluster string,
	response k8sapi.MaintenanceStatusResponse,
	output string,
) error {
	dashboardUrl := k8sDashboardURL(cluster)
	if output == "json" {
		return k8sPrintJson(k8sMaintenanceStatusOutput{
			MaintenanceStatusResponse: response,
			DashboardUrl:              dashboardUrl,
		})
	}

	sections := termio.TableSet{}
	summary := k8sClusterFrame(cluster)
	summary.AppendField("Dashboard", outputLink(dashboardUrl))
	summary.AppendField("Kubernetes upgrades", k8sOnOffLabel(response.Policy.K8sEnabled))
	summary.AppendField("Operating-system updates", k8sOnOffLabel(response.Policy.OsEnabled))
	summary.AppendField("Maintenance window", fmt.Sprintf(
		"%s %02d:00 - %02d:00 UTC",
		k8sMaintenanceDayLabel(response.Policy.WindowDay),
		response.Policy.WindowStartHour,
		(response.Policy.WindowStartHour+response.Policy.WindowDurationHours)%24,
	))
	if response.Schedule.NextWindowAt > 0 {
		now := time.Now().Unix()
		windowOpen := response.Schedule.NextWindowAt <= now && response.Schedule.NextWindowEnd > now
		if windowOpen {
			summary.AppendField("Maintenance window open until", k8sTimestamp(response.Schedule.NextWindowEnd))
		} else {
			summary.AppendField("Next window", k8sTimestamp(response.Schedule.NextWindowAt))
		}
	}
	if response.LastRun != nil {
		summary.AppendField("Last run", k8sMaintenanceLastRunLabel(*response.LastRun))
	}
	if response.OperationsError != "" && len(response.ActiveRuns) > 0 {
		summary.AppendFieldStyled("Node progress", termio.Danger("unavailable (%s)", response.OperationsError))
	}
	if response.BackupError != "" {
		summary.AppendFieldStyled("Last backup", termio.Danger("unavailable (%s)", response.BackupError))
	} else if response.Backup != nil && response.Backup.LastSuccessFinishedAt > 0 {
		summary.AppendField("Last backup", k8sTimestamp(response.Backup.LastSuccessFinishedAt))
	}
	summary.Print()
	fmt.Println()

	if len(response.ActiveRuns) > 0 {
		runs := outputTable("Run", "Maintenance", "Phase")
		runs.AppendHeaderEx("Started", termio.TableHeaderAlignRight)
		for _, run := range response.ActiveRuns {
			runs.Cell("%s", run.Uid)
			runs.Cell("%s", k8sMaintenanceRunTitle(run))
			runs.CellValue(k8sStatusPhaseStyle(run.Phase, k8sUpgradePhaseLabel(run.Phase)))
			runs.Cell("%s", k8sTimestamp(run.StartedAt))
		}
		sections.Add("Active runs", &runs)

		progress := k8sMaintenanceProgressTable(response.ActiveRuns)
		if progress != nil {
			sections.Add("Run progress", progress)
		}
	}

	sections.Print()
	return nil
}

func k8sMaintenanceProgressTable(runs []k8sapi.MaintenanceRunInfo) *termio.Table {
	hasItems := false
	for _, run := range runs {
		for _, task := range run.Tasks {
			if len(task.Items) > 0 {
				hasItems = true
				break
			}
		}
	}
	if !hasItems {
		return nil
	}

	table := outputTable("Task", "Node", "Group", "Phase", "Error")
	table.SetColumnWidthGroup(1, "node")
	table.SetColumnMaxWidth(4, 60)
	for _, run := range runs {
		for _, task := range run.Tasks {
			for _, item := range task.Items {
				table.Cell("%s", k8sMaintenanceTaskTitle(task))
				table.Cell("%s", termio.EscapeName(k8sValueOrDash(item.Node)))
				table.Cell("%s", k8sValueOrDash(item.Group))
				table.CellValue(k8sStatusPhaseStyle(item.Phase, k8sUpgradePhaseLabel(item.Phase)))
				if item.Error != "" {
					table.CellValue(termio.Danger("%s", item.Error))
				} else {
					table.CellValue(termio.Subtle("-"))
				}
			}
		}
	}
	return &table
}

func k8sMaintenanceRunTitle(run k8sapi.MaintenanceRunInfo) string {
	hasK8s := false
	hasOs := false
	for _, task := range run.Tasks {
		switch task.Kind {
		case k8sapi.MaintenanceTaskK8sUpgrade:
			hasK8s = true
		case k8sapi.MaintenanceTaskOsUpdate:
			hasOs = true
		}
	}
	switch {
	case hasK8s && hasOs:
		return "Kubernetes and operating-system update"
	case hasK8s:
		if len(run.Tasks) == 1 {
			return k8sMaintenanceTaskTitle(run.Tasks[0])
		}
		return "Kubernetes upgrade"
	case hasOs:
		return "Operating-system update"
	}
	if len(run.Tasks) == 1 {
		return k8sMaintenanceTaskTitle(run.Tasks[0])
	}
	if run.Kind == "node-action" {
		return "Node action"
	}
	return "Maintenance update"
}

func k8sMaintenanceTaskTitle(task k8sapi.MaintenanceTaskInfo) string {
	switch task.Kind {
	case k8sapi.MaintenanceTaskK8sUpgrade:
		if task.TargetRelease != "" {
			return "Kubernetes upgrade to " + task.TargetRelease
		}
		return "Kubernetes upgrade"
	case k8sapi.MaintenanceTaskOsUpdate:
		return "Operating-system update"
	case k8sapi.MaintenanceTaskCordon:
		return "Cordon"
	case k8sapi.MaintenanceTaskUncordon:
		return "Uncordon"
	case k8sapi.MaintenanceTaskRemove:
		if len(task.Items) > 1 {
			return fmt.Sprintf("Remove %d nodes", len(task.Items))
		}
		return "Remove node"
	}
	return task.Kind
}

func k8sMaintenanceLastRunLabel(run k8sapi.MaintenanceRunInfo) string {
	timestamp := run.FinishedAt
	if timestamp <= 0 {
		timestamp = run.RequestedAt
	}
	label := k8sMaintenanceRunTitle(run) + ", " + k8sUpgradePhaseLabel(run.Phase)
	if timestamp > 0 {
		label += " at " + k8sTimestamp(timestamp)
	}
	return label
}

func k8sPrintMaintenanceScheduleResult(
	cluster string,
	message string,
	response k8sapi.MaintenanceStatusResponse,
	output string,
) error {
	dashboardUrl := k8sDashboardURL(cluster)
	if output == "json" {
		return k8sPrintJson(k8sMaintenanceScheduleResult{
			Cluster:      cluster,
			Policy:       response.Policy,
			Schedule:     response.Schedule,
			DashboardUrl: dashboardUrl,
		})
	}

	frame := k8sSummaryFrame(message)
	frame.AppendField("Kubernetes upgrades", k8sOnOffLabel(response.Policy.K8sEnabled))
	frame.AppendField("Operating-system updates", k8sOnOffLabel(response.Policy.OsEnabled))
	frame.AppendField("Maintenance window", fmt.Sprintf(
		"%s %02d:00 - %02d:00 UTC",
		k8sMaintenanceDayLabel(response.Policy.WindowDay),
		response.Policy.WindowStartHour,
		(response.Policy.WindowStartHour+response.Policy.WindowDurationHours)%24,
	))
	if response.Schedule.NextWindowAt > 0 {
		now := time.Now().Unix()
		windowOpen := response.Schedule.NextWindowAt <= now && response.Schedule.NextWindowEnd > now
		if windowOpen {
			frame.AppendField("Maintenance window open until", k8sTimestamp(response.Schedule.NextWindowEnd))
		} else {
			frame.AppendField("Next window", k8sTimestamp(response.Schedule.NextWindowAt))
		}
	}
	frame.AppendField("Dashboard", outputLink(dashboardUrl))
	frame.Print()
	return nil
}

func k8sWaitForBackup(
	client *iapp.Client,
	cluster string,
	uid string,
	messages *k8sMessageState,
) error {
	ctx, cancel := context.WithTimeout(context.Background(), k8sBackupWaitTimeout)
	defer cancel()

	client.SetMessageHandler(nil)

	progressOutput := outputProgressWriter(messages.output == "json")
	progress := k8sStartWaitProgress(
		client,
		messages,
		progressOutput,
		fmt.Sprintf("Waiting for the backup of cluster %s", termio.QuoteName(cluster)),
	)
	defer k8sStopWaitProgress(client, progress)

	progress.SetItem(termio.ProgressItem{
		Key:    "backup",
		Status: "pending",
		State:  termio.ProgressPending,
	})

	lastPhase := ""
	for {
		response, err := iapp.Call(ctx, client, k8sapi.Status, util.Empty{})
		if err != nil {
			if !k8sWaitSleep(ctx) {
				return k8sBackupWaitTimeoutError(cluster)
			}
			continue
		}

		if err := k8sClusterFailure(response, cluster); err != nil {
			return err
		}

		backup := response.Backup
		if backup == nil {
			if !k8sWaitSleep(ctx) {
				return k8sBackupWaitTimeoutError(cluster)
			}
			continue
		}

		if backup.RequestFound && (uid == "" || backup.RequestUid == uid) {
			if backup.RequestPhase != lastPhase {
				lastPhase = backup.RequestPhase
				progress.SetItem(termio.ProgressItem{
					Key:    "backup",
					Status: backup.RequestPhase,
					State:  k8sBackupState(backup.RequestPhase),
				})
			}

			switch backup.RequestPhase {
			case k8sapi.BackupRequestCompleted:
				progress.Summary(outputSuccessText(progressOutput, "Backup completed"))
				return nil

			case k8sapi.BackupRequestFailed:
				reason := backup.RequestError
				if reason == "" {
					reason = "the backup failed"
				}
				return fmt.Errorf(
					"the backup failed: %s; use the dashboard at %s",
					reason,
					k8sValueOrDash(k8sDashboardURL(cluster)),
				)
			}
		} else if uid != "" && backup.ClaimRequestUid == uid {
			if lastPhase != k8sapi.BackupRequestRunning {
				lastPhase = k8sapi.BackupRequestRunning
				progress.SetItem(termio.ProgressItem{
					Key:    "backup",
					Status: k8sapi.BackupRequestRunning,
					State:  k8sBackupState(k8sapi.BackupRequestRunning),
				})
			}
		}

		if !k8sWaitSleep(ctx) {
			return k8sBackupWaitTimeoutError(cluster)
		}
	}
}

func k8sPrintBackupResult(
	cluster string,
	response k8sapi.BackupResponse,
	completed bool,
	output string,
) error {
	dashboardUrl := k8sDashboardURL(cluster)

	phase := response.Phase
	if completed {
		phase = k8sapi.BackupRequestCompleted
	}

	if output == "json" {
		return k8sPrintJson(k8sBackupResult{
			Cluster:      cluster,
			Uid:          response.Uid,
			Phase:        phase,
			DashboardUrl: dashboardUrl,
			Completed:    completed,
		})
	}

	if completed {
		return nil
	}

	message := fmt.Sprintf("Backup started for cluster %s", termio.QuoteName(cluster))
	frame := k8sSummaryFrame(message)
	frame.AppendField("Backup", response.Uid)
	frame.AppendField("Phase", phase)
	frame.AppendField("Dashboard", outputLink(dashboardUrl))
	frame.Print()
	return nil
}

func k8sUpgradePhaseLabel(phase string) string {
	switch phase {
	case k8sapi.MaintenanceRunPending:
		return "waiting"
	case k8sapi.MaintenanceRunPreflight:
		return "checking cluster"
	case k8sapi.MaintenanceRunBackup:
		return "waiting for backup"
	case k8sapi.MaintenanceRunRunning:
		return "in progress"
	case k8sapi.MaintenanceRunCompleted:
		return "completed"
	case k8sapi.MaintenanceRunBlocked:
		return "blocked"
	case k8sapi.MaintenanceRunFailed:
		return "failed"
	case k8sapi.MaintenanceRunCancelled:
		return "cancelled"
	default:
		return strings.ToLower(phase)
	}
}

func k8sMaintenanceWaitTimeoutError(cluster string) error {
	return fmt.Errorf(
		"the maintenance run did not finish within %s; use the dashboard at %s",
		k8sMaintenanceWaitTimeout,
		k8sValueOrDash(k8sDashboardURL(cluster)),
	)
}

func k8sBackupWaitTimeoutError(cluster string) error {
	return fmt.Errorf(
		"the backup did not finish within %s; use the dashboard at %s",
		k8sBackupWaitTimeout,
		k8sValueOrDash(k8sDashboardURL(cluster)),
	)
}

func k8sOnOffLabel(value bool) string {
	if value {
		return "on"
	}
	return "off"
}

func k8sTrimList(values []string) []string {
	result := make([]string, 0, len(values))
	for _, value := range values {
		value = strings.TrimSpace(value)
		if value != "" {
			result = append(result, value)
		}
	}
	return result
}

func k8sMaintenanceDayLabel(day string) string {
	switch strings.ToLower(strings.TrimSpace(day)) {
	case "mon":
		return "Monday"
	case "tue":
		return "Tuesday"
	case "wed":
		return "Wednesday"
	case "thu":
		return "Thursday"
	case "fri":
		return "Friday"
	case "sat":
		return "Saturday"
	case "sun":
		return "Sunday"
	default:
		return day
	}
}

func k8sTimestamp(seconds int64) string {
	if seconds <= 0 {
		return "-"
	}
	return time.Unix(seconds, 0).UTC().Format("2006-01-02 15:04 UTC")
}
