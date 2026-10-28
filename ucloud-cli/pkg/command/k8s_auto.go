package command

import (
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

type K8sClusterAutoCommand struct {
	Action    string `positional:"action" usage:"Action: show, set, run, stop, resume, pause, unpause or retry-os" required:"true"`
	Name      string `positional:"cluster" usage:"Cluster name" required:"true"`
	NodeName  string `positional:"node" usage:"Node name for retry-os"`
	K8s       string `flag:"k8s" usage:"Kubernetes updates: on or off (set only)"`
	Os        string `flag:"os" usage:"Operating-system updates: on or off (set only)"`
	Day       string `flag:"day" usage:"Weekly maintenance day: mon, tue, wed, thu, fri, sat or sun (set only)"`
	StartHour int    `flag:"start-hour" usage:"Weekly window start hour in UTC, 0 to 23 (set only)"`
	Days      int    `flag:"days" usage:"Pause length in days, 1 to 30 (pause only, default 1)"`
	Workspace string `flag:"workspace" usage:"Workspace to use"`
	Output    string `flag:"output" usage:"Output format: table or json"`
}

type k8sAutoOutput struct {
	Cluster      string                `json:"cluster"`
	DashboardUrl string                `json:"dashboardUrl"`
	PolicyFound  bool                  `json:"policyFound"`
	Policy       k8sapi.AutoPolicyInfo `json:"policy"`
	State        k8sapi.AutoStateInfo  `json:"state"`
}

type k8sAutoActionResult struct {
	Cluster      string `json:"cluster"`
	Action       string `json:"action"`
	Node         string `json:"node,omitempty"`
	Message      string `json:"message"`
	DeferUntil   int64  `json:"deferUntil,omitempty"`
	DashboardUrl string `json:"dashboardUrl"`
}

func (c *K8sClusterAutoCommand) HelpNotes() []string {
	return []string{
		"show prints the effective policy, the weekly window, the next run and the last run.",
		"The node argument is only used by retry-os.",
		"set changes only the flags you provide. At least one setting is required.",
		"pause and unpause control the weekly schedule. pause defaults to one day.",
		"retry-os retries a failed operating-system update and resumes maintenance if it was blocked.",
	}
}

func (c *K8sClusterAutoCommand) Execute() (resultErr error) {
	action := strings.ToLower(strings.TrimSpace(c.Action))
	switch action {
	case k8sapi.AutoActionShow,
		k8sapi.AutoActionSet,
		k8sapi.AutoActionRun,
		k8sapi.AutoActionStop,
		k8sapi.AutoActionResume,
		k8sapi.AutoActionPause,
		k8sapi.AutoActionUnpause,
		k8sapi.AutoActionRetryOs:
	default:
		return fmt.Errorf(
			"unknown automatic maintenance action %q; use show, set, run, stop, resume, pause, unpause or retry-os",
			c.Action,
		)
	}

	name := strings.TrimSpace(c.Name)
	if name == "" {
		return fmt.Errorf("a cluster name is required")
	}

	node := strings.TrimSpace(c.NodeName)
	if action == k8sapi.AutoActionRetryOs && node == "" {
		return fmt.Errorf("a node name is required for retry-os")
	}
	if action != k8sapi.AutoActionRetryOs && node != "" {
		return fmt.Errorf("a node name is only valid with retry-os")
	}

	k8sEnabled, k8sProvided, err := k8sAutoOnOff(c.K8s)
	if err != nil {
		return fmt.Errorf("--k8s: %w", err)
	}
	osEnabled, osProvided, err := k8sAutoOnOff(c.Os)
	if err != nil {
		return fmt.Errorf("--os: %w", err)
	}

	day := strings.ToLower(strings.TrimSpace(c.Day))
	if day != "" && !k8sapi.ValidAutoDay(day) {
		return fmt.Errorf("--day must be mon, tue, wed, thu, fri, sat or sun")
	}
	if c.StartHour < -1 || c.StartHour > 23 {
		return fmt.Errorf("--start-hour must be from 0 to 23")
	}

	request := k8sapi.AutoRequest{
		Action:     action,
		K8sEnabled: k8sEnabled,
		OsEnabled:  osEnabled,
		WindowDay:  day,
		Node:       node,
	}
	if c.StartHour >= 0 {
		request.WindowStartHour = util.OptValue(c.StartHour)
	}

	switch action {
	case k8sapi.AutoActionSet:
		if !k8sProvided && !osProvided && day == "" && c.StartHour < 0 {
			return fmt.Errorf("provide at least one of --k8s, --os, --day or --start-hour")
		}
	case k8sapi.AutoActionPause:
		if k8sProvided || osProvided || day != "" || c.StartHour >= 0 {
			return fmt.Errorf("--k8s, --os, --day and --start-hour are only valid with set")
		}

		days := c.Days
		if days < 0 {
			days = 1
		}
		if days < 1 || days > 30 {
			return fmt.Errorf("--days must be from 1 to 30")
		}
		request.PauseDays = days
	default:
		if k8sProvided || osProvided || day != "" || c.StartHour >= 0 {
			return fmt.Errorf("--k8s, --os, --day and --start-hour are only valid with set")
		}
		if c.Days >= 0 {
			return fmt.Errorf("--days is only valid with pause")
		}
	}

	_, output, err := k8sInitializeOutput(c.Workspace, c.Output)
	if err != nil {
		return err
	}

	connectCtx, connectCancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer connectCancel()

	client, err := k8sConnectNamedCluster(connectCtx, name)
	if err != nil {
		return err
	}
	defer client.Close()

	messages := k8sStartMessages(client, output)
	defer func() {
		resultErr = k8sStopMessages(client, messages, resultErr)
	}()

	callCtx, callCancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer callCancel()

	response, err := iapp.Call(callCtx, client, k8sapi.AutoMaintenance, request)
	if err != nil {
		return errors.New(k8sClusterMessage(k8sErrorMessage(err)))
	}

	if action == k8sapi.AutoActionShow || action == k8sapi.AutoActionSet {
		if action == k8sapi.AutoActionSet && output != "json" {
			fmt.Fprintln(os.Stdout, outputSuccessText(os.Stdout, "Automatic maintenance policy updated"))
		}
		return k8sPrintAutoStatus(name, response, output)
	}

	return k8sPrintAutoAction(name, action, node, response, output)
}

func k8sAutoOnOff(value string) (util.Option[bool], bool, error) {
	value = strings.ToLower(strings.TrimSpace(value))
	switch value {
	case "":
		return util.OptNone[bool](), false, nil
	case "on", "true", "yes":
		return util.OptValue(true), true, nil
	case "off", "false", "no":
		return util.OptValue(false), true, nil
	default:
		return util.OptNone[bool](), false, fmt.Errorf("use on or off")
	}
}

func k8sPrintAutoStatus(cluster string, response k8sapi.AutoResponse, output string) error {
	dashboardUrl := k8sDashboardURL(cluster)

	if output == "json" {
		return k8sPrintJson(k8sAutoOutput{
			Cluster:      cluster,
			DashboardUrl: dashboardUrl,
			PolicyFound:  response.PolicyFound,
			Policy:       response.Policy,
			State:        response.State,
		})
	}

	summary := k8sClusterFrame(cluster)
	summary.AppendField("Dashboard", outputLink(dashboardUrl))
	if response.PolicyFound {
		summary.AppendField("Policy", "stored")
	} else {
		summary.AppendField("Policy", "default (not stored yet)")
	}
	summary.AppendField("Kubernetes updates", k8sAutoOnOffLabel(response.Policy.K8sEnabled))
	summary.AppendField("Operating-system updates", k8sAutoOnOffLabel(response.Policy.OsEnabled))
	summary.AppendField("Reboot policy", k8sAutoRebootPolicyLabel(response.Policy.RebootPolicy))
	summary.AppendField("Window", k8sAutoWindowLabel(response.Policy))
	if response.Policy.DeferUntil > 0 {
		summary.AppendField("Deferred until", k8sAutoTimestamp(response.Policy.DeferUntil))
	} else {
		summary.AppendField("Deferred until", "-")
	}
	summary.AppendField("Next run", k8sAutoRunWindowLabel(response.Policy))
	summary.AppendField("Automatic maintenance", k8sAutoStateLabel(response.Policy, response.State))
	summary.AppendField("Last run", k8sAutoLastRunLabel(response.State.LastRun))
	if response.State.LastRun.Error != "" {
		summary.AppendFieldStyled("Last run error", termio.Danger("%s", response.State.LastRun.Error))
	}

	if response.State.OsRecoveryError != "" {
		summary.AppendFieldStyled("OS recovery", termio.Danger("unavailable (%s)", response.State.OsRecoveryError))
	} else if len(response.State.OsRecoveryNodes) == 0 {
		summary.AppendField("OS recovery", "none")
	} else {
		nodes := make([]string, 0, len(response.State.OsRecoveryNodes))
		for _, node := range response.State.OsRecoveryNodes {
			nodes = append(nodes, termio.EscapeName(node))
		}
		summary.AppendField("OS recovery", strings.Join(nodes, ", "))
	}

	sections := termio.TableSet{}

	if len(response.State.OsReports) > 0 {
		reports := outputTable("Node", "Pending", "Security", "Reboot required", "Checked", "Error")
		for _, report := range response.State.OsReports {
			reports.Cell("%s", termio.EscapeName(k8sValueOrDash(report.NodeName)))
			reports.Cell("%d", report.Pending)
			reports.Cell("%d", report.Security)
			reports.Cell("%s", k8sAutoBoolLabel(report.RebootRequired))
			reports.Cell("%s", k8sAutoTimestamp(report.CheckedAt))
			reports.Cell("%s", k8sValueOrDash(report.Error))
		}
		sections.Add("OS reports", &reports)
	}

	if len(response.State.LastRun.Checks) > 0 {
		checks := outputTable("Run check", "OK", "Detail")
		for _, check := range response.State.LastRun.Checks {
			checks.Cell("%s", termio.EscapeName(check.Name))
			checks.Cell("%s", k8sAutoBoolLabel(check.Ok))
			checks.Cell("%s", k8sValueOrDash(check.Detail))
		}
		sections.Add("Last run checks", &checks)
	}

	summary.Print()
	if len(response.State.OsReports) > 0 || len(response.State.LastRun.Checks) > 0 {
		fmt.Println()
		sections.Print()
	}

	return nil
}

func k8sPrintAutoAction(
	cluster string,
	action string,
	node string,
	response k8sapi.AutoResponse,
	output string,
) error {
	dashboardUrl := k8sDashboardURL(cluster)
	message := k8sAutoActionMessage(action, node, response)

	if output == "json" {
		return k8sPrintJson(k8sAutoActionResult{
			Cluster:      cluster,
			Action:       action,
			Node:         node,
			Message:      message,
			DeferUntil:   response.Policy.DeferUntil,
			DashboardUrl: dashboardUrl,
		})
	}

	displayNode := node
	if node != "" {
		displayNode = termio.QuoteName(node)
	}
	displayMessage := k8sAutoActionMessage(action, displayNode, response)
	frame := k8sActionResultFrame(cluster, false, displayMessage)
	frame.AppendField("Action", action)
	if node != "" {
		frame.AppendField("Node", termio.EscapeName(node))
	}
	frame.AppendField("Dashboard", outputLink(dashboardUrl))
	frame.Print()
	return nil
}

func k8sAutoActionMessage(action string, node string, response k8sapi.AutoResponse) string {
	switch action {
	case k8sapi.AutoActionRun:
		return "Automatic maintenance started."
	case k8sapi.AutoActionStop:
		return "The waiting automatic run was stopped."
	case k8sapi.AutoActionResume:
		return "Automatic maintenance resumed."
	case k8sapi.AutoActionPause:
		if response.Policy.DeferUntil > 0 {
			return "The weekly schedule is paused until " + k8sAutoTimestamp(response.Policy.DeferUntil) + "."
		}
		return "The weekly schedule is paused."
	case k8sapi.AutoActionUnpause:
		return "The weekly schedule resumed."
	case k8sapi.AutoActionRetryOs:
		return "The operating-system update was retried on " + node + "."
	default:
		return "Automatic maintenance was updated."
	}
}

var k8sAutoDayLabels = map[string]string{
	"mon": "Monday",
	"tue": "Tuesday",
	"wed": "Wednesday",
	"thu": "Thursday",
	"fri": "Friday",
	"sat": "Saturday",
	"sun": "Sunday",
}

func k8sAutoDayLabel(day string) string {
	if label, ok := k8sAutoDayLabels[strings.ToLower(strings.TrimSpace(day))]; ok {
		return label
	}
	return day
}

func k8sAutoWindowLabel(policy k8sapi.AutoPolicyInfo) string {
	days := make([]string, 0, len(policy.WindowDays))
	for _, day := range policy.WindowDays {
		days = append(days, k8sAutoDayLabel(day))
	}
	if len(days) == 0 {
		days = append(days, "Sunday")
	}

	endHour := (policy.WindowStartHour + policy.WindowDurationHours) % 24
	nextDay := ""
	if endHour <= policy.WindowStartHour {
		nextDay = " (next day)"
	}
	return fmt.Sprintf(
		"%s %02d:00-%02d:00 UTC%s",
		strings.Join(days, ", "),
		policy.WindowStartHour,
		endHour,
		nextDay,
	)
}

func k8sAutoRunWindowLabel(policy k8sapi.AutoPolicyInfo) string {
	if policy.NextRunAt == 0 {
		return "unknown"
	}
	if policy.NextRunEnd == 0 {
		return k8sAutoTimestamp(policy.NextRunAt)
	}
	return k8sAutoTimestamp(policy.NextRunAt) + " - " + k8sAutoTimestamp(policy.NextRunEnd)
}

func k8sAutoStateLabel(policy k8sapi.AutoPolicyInfo, state k8sapi.AutoStateInfo) string {
	if !policy.K8sEnabled && !policy.OsEnabled {
		return "off"
	}
	if state.Blocked {
		return "blocked"
	}
	if k8sAutoRunActive(state.LastRun) {
		return "in progress"
	}
	return "scheduled"
}

func k8sAutoLastRunLabel(run k8sapi.AutoRunInfo) string {
	if run.Uid == "" {
		return "never"
	}

	channel := run.Channel
	switch run.Channel {
	case k8sapi.AutoChannelK8s:
		channel = "Kubernetes"
	case k8sapi.AutoChannelOs:
		channel = "operating system"
	}

	label := channel + " " + k8sAutoPhaseLabel(run.Phase)
	if run.TargetRelease != "" {
		label += " to " + run.TargetRelease
	}
	if run.FinishedAt > 0 {
		label += " (" + k8sAutoTimestamp(run.FinishedAt) + ")"
	}
	return label
}

func k8sAutoPhaseLabel(phase string) string {
	switch phase {
	case k8sapi.AutoPhasePending:
		return "waiting"
	case k8sapi.AutoPhasePreflight:
		return "checking the cluster"
	case k8sapi.AutoPhaseBackup:
		return "waiting for a backup"
	case k8sapi.AutoPhaseRunning:
		return "in progress"
	case k8sapi.AutoPhaseCompleted:
		return "completed"
	case k8sapi.AutoPhaseBlocked:
		return "stopped"
	default:
		return phase
	}
}

func k8sAutoRunActive(run k8sapi.AutoRunInfo) bool {
	switch run.Phase {
	case k8sapi.AutoPhasePending,
		k8sapi.AutoPhasePreflight,
		k8sapi.AutoPhaseBackup,
		k8sapi.AutoPhaseRunning:
		return true
	}
	return false
}

func k8sAutoOnOffLabel(value bool) string {
	if value {
		return "on"
	}
	return "off"
}

func k8sAutoBoolLabel(value bool) string {
	if value {
		return "yes"
	}
	return "no"
}

func k8sAutoRebootPolicyLabel(value string) string {
	switch value {
	case k8sapi.AutoRebootWhenRequired:
		return "when required"
	case k8sapi.AutoRebootAlways:
		return "always"
	default:
		return value
	}
}

func k8sAutoTimestamp(seconds int64) string {
	if seconds <= 0 {
		return "-"
	}
	return time.Unix(seconds, 0).UTC().Format("2006-01-02 15:04 UTC")
}
