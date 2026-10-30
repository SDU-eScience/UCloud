package command

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	fnd "ucloud.dk/shared/pkg/foundation"
	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/iapp"
)

const (
	k8sRecoveryJobWaitTimeout = 10 * time.Minute
	k8sRecoveryStartTimeout   = 45 * time.Minute
	k8sRecoveryCallTimeout    = 5 * time.Minute
	k8sRecoveryWaitTimeout    = 2 * time.Hour
	k8sRecoverySessionTimeout = k8sRecoveryStartTimeout + k8sRecoveryWaitTimeout + 10*time.Minute
	k8sRecoveryPollInterval   = 10 * time.Second
)

type K8sClusterRecoveryCommand struct {
	Action          string `positional:"action" usage:"Action: status or start" required:"true"`
	Name            string `positional:"cluster" usage:"Cluster name" required:"true"`
	Backup          string `flag:"backup" usage:"Backup id to restore (default: active or newest)"`
	AcknowledgeRisk bool   `flag:"acknowledge-risk" usage:"Accept a backup with different cluster credentials"`
	Wait            bool   `flag:"wait" usage:"Wait until verification finishes"`
	Workspace       string `flag:"workspace" usage:"Workspace to use"`
	Output          string `flag:"output" usage:"Output format: table or json"`
}

type k8sRecoveryInactiveOutput struct {
	Cluster      string `json:"cluster"`
	DashboardUrl string `json:"dashboardUrl"`
	Supported    bool   `json:"supported"`
	Active       bool   `json:"active"`
	Message      string `json:"message"`
}

type k8sRecoveryStatusOutput struct {
	Cluster      string `json:"cluster"`
	DashboardUrl string `json:"dashboardUrl"`
	k8sapi.RecoveryStatusResponse
}

type k8sRecoveryStartOutput struct {
	Cluster      string                     `json:"cluster"`
	DashboardUrl string                     `json:"dashboardUrl"`
	BackupId     string                     `json:"backupId,omitempty"`
	Phase        string                     `json:"phase"`
	Stage        string                     `json:"stage"`
	Message      string                     `json:"message,omitempty"`
	Checks       []k8sapi.RecoveryCheckInfo `json:"checks,omitempty"`
	Completed    bool                       `json:"completed"`
}

func (c *K8sClusterRecoveryCommand) HelpNotes() []string {
	return []string{
		"status shows the recovery job, the current stage, the backups and the verification checks.",
		"start starts the recovery job when it is not running, then runs the existing restore flow.",
		"Without --backup, start uses the active recovery backup or the newest backup.",
		"--acknowledge-risk accepts a backup that uses different cluster credentials.",
		"--wait follows the restore until verification finishes. Close recovery in the dashboard afterwards.",
	}
}

func (c *K8sClusterRecoveryCommand) Execute() error {
	action := strings.ToLower(strings.TrimSpace(c.Action))
	switch action {
	case k8sapi.RecoveryActionStatus, k8sapi.RecoveryActionStart:
	default:
		return fmt.Errorf("unknown recovery action %q; use status or start", c.Action)
	}

	name := strings.TrimSpace(c.Name)
	if name == "" {
		return fmt.Errorf("a cluster name is required")
	}

	backup := strings.TrimSpace(c.Backup)
	if action == k8sapi.RecoveryActionStatus {
		if backup != "" || c.AcknowledgeRisk || c.Wait {
			return fmt.Errorf("--backup, --acknowledge-risk and --wait are only valid with start")
		}
	}

	cfg, err := k8sInitialize(c.Workspace)
	if err != nil {
		return err
	}
	output, err := k8sOutputFormat(c.Output, cfg)
	if err != nil {
		return err
	}

	stack, err := k8sRetrieveCluster(name)
	if err != nil {
		return err
	}

	status := stack.Status.GetOrDefault(orcapi.StackStatus{})

	if action == k8sapi.RecoveryActionStatus {
		if !status.DisasterRecoveryDeclared || !k8sRecoveryJobRunning(stack) {
			return k8sPrintRecoveryInactive(name, status.DisasterRecoveryDeclared, output)
		}

		connectCtx, connectCancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
		defer connectCancel()

		client, err := k8sConnectRecovery(connectCtx, stack)
		if err != nil {
			return err
		}
		defer client.Close()

		callCtx, callCancel := context.WithTimeout(context.Background(), k8sRecoveryCallTimeout)
		defer callCancel()

		response, err := iapp.Call(callCtx, client, k8sapi.RecoveryStatus, util.Empty{})
		if err != nil {
			return errors.New(k8sClusterMessage(k8sErrorMessage(err)))
		}

		return k8sPrintRecoveryStatus(name, response, output)
	}

	if !status.DisasterRecoveryDeclared {
		return fmt.Errorf("the Kubernetes application of %s does not declare disaster recovery", name)
	}

	if err := k8sSpawnRecoveryJob(name, stack); err != nil {
		return err
	}

	client, sessionCancel, err := k8sWaitForRecoveryClient(name)
	if err != nil {
		return err
	}
	defer sessionCancel()
	defer client.Close()

	client.SetMessageHandler(k8sPrintProgressMessage)

	request := k8sapi.RecoveryStartRequest{
		BackupId:        backup,
		AcknowledgeRisk: c.AcknowledgeRisk,
	}

	callCtx, callCancel := context.WithTimeout(context.Background(), k8sRecoveryStartTimeout)
	response, err := iapp.Call(callCtx, client, k8sapi.RecoveryStart, request)
	callCancel()
	if err != nil {
		return errors.New(k8sClusterMessage(k8sErrorMessage(err)))
	}

	completed := false
	if c.Wait {
		response, err = k8sWaitForRecovery(client, name, response, c.AcknowledgeRisk)
		if err != nil {
			return err
		}
		completed = true
	}

	return k8sPrintRecoveryStart(name, response, completed, output)
}

func k8sRecoveryJobRunning(stack orcapi.Stack) bool {
	status := stack.Status.GetOrDefault(orcapi.StackStatus{})
	for _, job := range status.Jobs {
		if !strings.EqualFold(job.Specification.Labels[orcapi.ResourceLabelDisasterRecovery], "true") {
			continue
		}
		if job.Status.State == orcapi.JobStateRunning {
			return true
		}
	}
	return false
}

func k8sSpawnRecoveryJob(name string, stack orcapi.Stack) error {
	if k8sRecoveryJobRunning(stack) {
		return nil
	}

	k8sPrintStatusLine(ucxapi.UiMessageLevelProgress, "Starting the recovery job")

	_, httpErr := orcapi.StacksSpawnDeclaredJob.Invoke(fnd.FindByStringId{Id: name})
	if httpErr.AsError() != nil && httpErr.StatusCode != http.StatusConflict {
		return fmt.Errorf("could not start the recovery job: %s", httpErr.Why)
	}
	return nil
}

func k8sWaitForRecoveryClient(name string) (*iapp.Client, context.CancelFunc, error) {
	ctx, cancel := context.WithTimeout(context.Background(), k8sRecoveryJobWaitTimeout)
	defer cancel()

	k8sPrintStatusLine(ucxapi.UiMessageLevelProgress, "Waiting for the cluster dashboard to become available")

	var lastErr error
	for {
		stack, err := k8sRetrieveCluster(name)
		if err != nil {
			lastErr = err
		} else if k8sRecoveryJobRunning(stack) {
			sessionCtx, sessionCancel := context.WithTimeout(context.Background(), k8sRecoverySessionTimeout)
			client, connectErr := k8sConnectRecovery(sessionCtx, stack)
			if connectErr == nil {
				return client, sessionCancel, nil
			}
			sessionCancel()
			lastErr = connectErr
		} else {
			lastErr = fmt.Errorf("the recovery job is not running yet")
		}

		if !k8sWaitSleep(ctx) {
			message := "the recovery job did not start"
			if lastErr != nil {
				message = k8sErrorMessage(lastErr)
			}
			return nil, nil, fmt.Errorf(
				"could not connect to the recovery job for %s: %s; use the dashboard at %s",
				name,
				message,
				k8sValueOrDash(k8sDashboardURL(name)),
			)
		}
	}
}

func k8sWaitForRecovery(
	client *iapp.Client,
	name string,
	response k8sapi.RecoveryStartResponse,
	acknowledgeRisk bool,
) (k8sapi.RecoveryStartResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), k8sRecoveryWaitTimeout)
	defer cancel()

	client.SetMessageHandler(nil)

	progress := termio.NewProgressList(
		os.Stderr,
		fmt.Sprintf("Waiting for recovery of cluster %s", name),
	)
	progress.Start()
	defer progress.Stop()

	progress.SetItem(termio.ProgressItem{
		Key:    "recovery",
		Status: k8sRecoveryStageStatus(response.Stage),
		State:  termio.ProgressRunning,
	})

	lastStage := ""
	for {
		if response.Stage == k8sapi.RecoveryStageVerify {
			progress.Summary("Recovery verification reached")
			return response, nil
		}

		if response.Phase != k8sapi.RecoveryPhaseActive {
			progress.SetItem(termio.ProgressItem{
				Key:    "recovery",
				Status: k8sRecoveryPhaseLabel(response.Phase),
				State:  termio.ProgressFailure,
			})
			return response, fmt.Errorf(
				"the recovery ended in phase %q; use the dashboard at %s",
				k8sRecoveryPhaseLabel(response.Phase),
				k8sValueOrDash(k8sDashboardURL(name)),
			)
		}

		if response.Stage != lastStage {
			lastStage = response.Stage
			progress.SetItem(termio.ProgressItem{
				Key:    "recovery",
				Status: k8sRecoveryStageStatus(lastStage),
				State:  termio.ProgressRunning,
			})
		}

		if !k8sRecoverySleep(ctx) {
			return response, k8sRecoveryWaitTimeoutError(name)
		}

		callCtx, callCancel := context.WithTimeout(ctx, k8sRecoveryCallTimeout)
		next, err := iapp.Call(callCtx, client, k8sapi.RecoveryStart, k8sapi.RecoveryStartRequest{
			BackupId:        response.BackupId,
			AcknowledgeRisk: acknowledgeRisk,
		})
		callCancel()
		if err != nil {
			progress.SetItem(termio.ProgressItem{
				Key:    "recovery",
				Status: k8sErrorMessage(err),
				State:  termio.ProgressFailure,
			})
			return response, errors.New(k8sClusterMessage(k8sErrorMessage(err)))
		}
		response = next
	}
}

func k8sRecoveryStageStatus(stage string) string {
	if stage == k8sapi.RecoveryStageRestore {
		return "waiting for the cluster to respond"
	}
	return k8sRecoveryStageLabel(stage)
}

func k8sRecoverySleep(ctx context.Context) bool {
	timer := time.NewTimer(k8sRecoveryPollInterval)
	defer timer.Stop()

	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}

func k8sRecoveryWaitTimeoutError(name string) error {
	return fmt.Errorf(
		"the recovery did not reach verification within %s; use the dashboard at %s",
		k8sRecoveryWaitTimeout,
		k8sValueOrDash(k8sDashboardURL(name)),
	)
}

func k8sPrintRecoveryInactive(name string, supported bool, output string) error {
	message := "The recovery job is not running."
	if !supported {
		message = "This cluster does not declare disaster recovery."
	}

	if output == "json" {
		return k8sPrintJson(k8sRecoveryInactiveOutput{
			Cluster:      name,
			DashboardUrl: k8sDashboardURL(name),
			Supported:    supported,
			Active:       false,
			Message:      message,
		})
	}

	supportedLabel := "no"
	if supported {
		supportedLabel = "yes"
	}

	frame := termio.Frame{}
	frame.Title("Cluster " + name)
	if supported {
		frame.AppendFieldStyled("Status", termio.Subtle("Recovery not active"))
	} else {
		frame.AppendFieldStyled("Status", termio.Warning("Not supported"))
	}
	frame.AppendField("Supported", supportedLabel)
	frame.AppendField("Message", message)
	frame.AppendField("Dashboard", k8sValueOrDash(k8sDashboardURL(name)))
	frame.Print()
	return nil
}

func k8sPrintRecoveryStatus(name string, response k8sapi.RecoveryStatusResponse, output string) error {
	if output == "json" {
		return k8sPrintJson(k8sRecoveryStatusOutput{
			Cluster:                name,
			DashboardUrl:           k8sDashboardURL(name),
			RecoveryStatusResponse: response,
		})
	}

	summary := termio.Frame{}
	summary.Title("Cluster " + name)
	summary.AppendField("Dashboard", k8sValueOrDash(k8sDashboardURL(name)))
	summary.AppendFieldStyled("Recovery", k8sRecoveryStatusValue(response))
	if response.RecoveryFound {
		summary.AppendField("Stage", k8sRecoveryStageLabel(response.Stage))
		summary.AppendField("Backup", k8sValueOrDash(response.BackupId))
		summary.AppendField("Backup release", k8sValueOrDash(response.BackupRelease))
		summary.AppendField("Credentials", k8sRecoveryCredentialsLabel(response.CredentialsStatus))
		summary.AppendField("Started", k8sAutoTimestamp(response.StartedAt))
		if response.StartedBy != "" {
			summary.AppendField("Started by", response.StartedBy)
		}
		if response.ClosedAt > 0 {
			summary.AppendField("Closed", k8sAutoTimestamp(response.ClosedAt))
		}
		if response.LastError != "" {
			summary.AppendFieldStyled("Last error", termio.Danger("%s", response.LastError))
		}
	} else if response.SelectedBackupId != "" {
		summary.AppendField("Selected backup", response.SelectedBackupId)
		summary.AppendField("Credentials", k8sRecoveryCredentialsLabel(response.CredentialsStatus))
	}
	summary.AppendField("Cluster phase", k8sValueOrDash(response.ClusterPhase))
	if len(response.Messages) > 0 {
		summary.AppendField("Messages", strings.Join(response.Messages, " "))
	}
	summary.Print()

	sections := termio.TableSet{}

	if len(response.Backups) > 0 {
		backups := termio.Table{}
		backups.AppendHeader("Backup")
		backups.AppendHeader("Release")
		backups.AppendHeader("Created")
		backups.AppendHeader("Size")
		backups.AppendHeader("Created by node")
		backups.AppendHeader("Selected")
		for _, backup := range response.Backups {
			selected := ""
			if backup.Id == response.SelectedBackupId {
				selected = "yes"
			}
			backups.Cell("%s", backup.Id)
			backups.Cell("%s", k8sValueOrDash(backup.Release))
			backups.Cell("%s", k8sAutoTimestamp(backup.CreatedAt))
			backups.Cell("%s", k8sFormatBytes(backup.SizeBytes))
			backups.Cell("%s", k8sValueOrDash(backup.CreatedByNode))
			backups.Cell("%s", selected)
		}
		sections.Add("Backups", &backups)
	}

	if checks := k8sRecoveryChecksTable(response.Checks); checks != nil {
		sections.Add("Checks", checks)
	}

	if len(response.Backups) > 0 || len(response.Checks) > 0 {
		fmt.Println()
		sections.Print()
	}

	return nil
}

func k8sRecoveryStatusValue(response k8sapi.RecoveryStatusResponse) termio.StyledString {
	if response.Active {
		return termio.Success("active")
	}
	if response.RecoveryFound {
		return termio.Subtle("%s", k8sRecoveryPhaseLabel(response.Phase))
	}
	return termio.Subtle("not started")
}

func k8sPrintRecoveryStart(
	name string,
	response k8sapi.RecoveryStartResponse,
	completed bool,
	output string,
) error {
	if output == "json" {
		return k8sPrintJson(k8sRecoveryStartOutput{
			Cluster:      name,
			DashboardUrl: k8sDashboardURL(name),
			BackupId:     response.BackupId,
			Phase:        response.Phase,
			Stage:        response.Stage,
			Message:      response.Message,
			Checks:       response.Checks,
			Completed:    completed,
		})
	}

	k8sPrintResultSpacer()

	frame := termio.Frame{}
	frame.Title("Cluster " + name)
	if completed {
		frame.AppendFieldStyled("Status", termio.Success("Verification reached"))
	} else {
		frame.AppendFieldStyled("Status", termio.Subtle("Recovery started"))
	}
	frame.AppendField("Dashboard", k8sValueOrDash(k8sDashboardURL(name)))
	frame.AppendField("Backup", k8sValueOrDash(response.BackupId))
	frame.AppendField("Phase", k8sRecoveryPhaseLabel(response.Phase))
	frame.AppendField("Stage", k8sRecoveryStageLabel(response.Stage))
	if response.Message != "" {
		frame.AppendField("Message", response.Message)
	}
	if completed {
		frame.AppendFieldStyled(
			"Note",
			termio.Warning("Close recovery in the dashboard to return the cluster to normal operation."),
		)
	}
	frame.Print()

	if checks := k8sRecoveryChecksTable(response.Checks); checks != nil {
		fmt.Println()
		sections := termio.TableSet{}
		sections.Add("Checks", checks)
		sections.Print()
	}

	return nil
}

func k8sRecoveryChecksTable(checks []k8sapi.RecoveryCheckInfo) *termio.Table {
	if len(checks) == 0 {
		return nil
	}

	table := &termio.Table{}
	table.AppendHeader("Check")
	table.AppendHeader("Status")
	table.AppendHeader("Detail")
	for _, check := range checks {
		table.Cell("%s", check.Name)
		table.Cell("%s", check.Status)
		table.Cell("%s", k8sValueOrDash(check.Detail))
	}
	return table
}

func k8sRecoveryPhaseLabel(phase string) string {
	switch phase {
	case k8sapi.RecoveryPhaseActive:
		return "active"
	case k8sapi.RecoveryPhaseClosed:
		return "closed"
	default:
		return k8sValueOrDash(phase)
	}
}

func k8sRecoveryStageLabel(stage string) string {
	switch stage {
	case k8sapi.RecoveryStageStop:
		return "select backup"
	case k8sapi.RecoveryStageRecreate:
		return "review recovery"
	case k8sapi.RecoveryStageRestore:
		return "restore"
	case k8sapi.RecoveryStageVerify:
		return "verification"
	default:
		return k8sValueOrDash(stage)
	}
}

func k8sRecoveryCredentialsLabel(status string) string {
	switch status {
	case k8sapi.RecoveryCredentialsOk:
		return "ok"
	case k8sapi.RecoveryCredentialsMismatch:
		return "mismatch"
	default:
		return "unchecked"
	}
}

func k8sFormatBytes(value int64) string {
	if value <= 0 {
		return "-"
	}

	units := []string{"B", "KiB", "MiB", "GiB", "TiB"}
	size := float64(value)
	unit := 0
	for size >= 1024 && unit < len(units)-1 {
		size /= 1024
		unit++
	}

	if unit == 0 {
		return fmt.Sprintf("%d %s", value, units[unit])
	}
	return fmt.Sprintf("%.1f %s", size, units[unit])
}
