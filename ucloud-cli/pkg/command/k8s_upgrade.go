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

const (
	k8sUpgradeWaitTimeout = 4 * time.Hour
	k8sBackupWaitTimeout  = 60 * time.Minute
)

type K8sClusterUpgradeCommand struct {
	Name       string   `positional:"name" usage:"Cluster name" required:"true"`
	K8sVersion string   `flag:"k8s-version" usage:"Target Kubernetes release (required)"`
	Pools      []string `flag:"pool" usage:"Limit cordon or drain preparation to this pool"`
	Cordon     bool     `flag:"cordon" usage:"Cordon nodes before the upgrade"`
	Drain      bool     `flag:"drain" usage:"Cordon and drain nodes before the upgrade"`
	Timeout    int      `flag:"timeout" usage:"Drain timeout in seconds, 30 to 3600 (default 600)"`
	Wait       bool     `flag:"wait" usage:"Wait until the rolling upgrade finishes"`
	Workspace  string   `flag:"workspace" usage:"Workspace to use"`
	Output     string   `flag:"output" usage:"Output format: table or json"`
}

type K8sClusterBackupCommand struct {
	Name      string `positional:"name" usage:"Cluster name" required:"true"`
	Wait      bool   `flag:"wait" usage:"Wait until the backup finishes"`
	Workspace string `flag:"workspace" usage:"Workspace to use"`
	Output    string `flag:"output" usage:"Output format: table or json"`
}

type k8sUpgradeResult struct {
	Cluster       string `json:"cluster"`
	TargetRelease string `json:"targetRelease"`
	Uid           string `json:"uid,omitempty"`
	DashboardUrl  string `json:"dashboardUrl"`
	Completed     bool   `json:"completed"`
}

type k8sBackupResult struct {
	Cluster      string `json:"cluster"`
	Uid          string `json:"uid"`
	Phase        string `json:"phase"`
	DashboardUrl string `json:"dashboardUrl"`
	Completed    bool   `json:"completed"`
}

func (c *K8sClusterUpgradeCommand) HelpNotes() []string {
	return []string{
		"The rolling upgrade covers the whole cluster, control plane first, then worker pools.",
		"--pool only limits which pools receive --cordon or --drain. It does not limit which nodes upgrade.",
		"With no --pool, the preparation applies to every node group.",
	}
}

func (c *K8sClusterUpgradeCommand) Execute() error {
	name := strings.TrimSpace(c.Name)
	if name == "" {
		return fmt.Errorf("a cluster name is required")
	}

	version := strings.TrimSpace(c.K8sVersion)
	if version == "" {
		return fmt.Errorf("--k8s-version is required")
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

	pools := make([]string, 0, len(c.Pools))
	for _, pool := range c.Pools {
		pool = strings.TrimSpace(pool)
		if pool != "" {
			pools = append(pools, pool)
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

	connectCtx, connectCancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer connectCancel()

	stack, err := k8sRetrieveCluster(name)
	if err != nil {
		return err
	}

	client, err := k8sConnectCluster(connectCtx, stack)
	if err != nil {
		return err
	}
	defer client.Close()

	client.SetMessageHandler(k8sPrintProgressMessage)

	callCtx, callCancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer callCancel()

	response, err := iapp.Call(callCtx, client, k8sapi.RollingUpgrade, k8sapi.UpgradeRequest{
		K8sVersion:     version,
		Pools:          pools,
		TimeoutSeconds: timeout,
		Cordon:         c.Cordon,
		Drain:          c.Drain,
	})
	if err != nil {
		return errors.New(k8sClusterMessage(k8sErrorMessage(err)))
	}

	completed := false
	if c.Wait {
		if err := k8sWaitForUpgrade(client, name, response.Uid, response.TargetRelease); err != nil {
			return err
		}
		completed = true
	}

	return k8sPrintUpgradeResult(name, response, completed, output)
}

func (c *K8sClusterBackupCommand) HelpNotes() []string {
	return []string{
		"The backup runs from a control-plane node and does not change the hourly schedule.",
		"Use --wait to block until the manual backup finishes.",
	}
}

func (c *K8sClusterBackupCommand) Execute() error {
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

	connectCtx, connectCancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer connectCancel()

	stack, err := k8sRetrieveCluster(name)
	if err != nil {
		return err
	}

	client, err := k8sConnectCluster(connectCtx, stack)
	if err != nil {
		return err
	}
	defer client.Close()

	client.SetMessageHandler(k8sPrintProgressMessage)

	callCtx, callCancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer callCancel()

	response, err := iapp.Call(callCtx, client, k8sapi.BackupNow, util.Empty{})
	if err != nil {
		return errors.New(k8sClusterMessage(k8sErrorMessage(err)))
	}

	completed := false
	if c.Wait {
		if err := k8sWaitForBackup(client, name, response.Uid); err != nil {
			return err
		}
		completed = true
	}

	return k8sPrintBackupResult(name, response, completed, output)
}

func k8sWaitForUpgrade(client *iapp.Client, cluster string, uid string, target string) error {
	ctx, cancel := context.WithTimeout(context.Background(), k8sUpgradeWaitTimeout)
	defer cancel()

	client.SetMessageHandler(nil)

	progress := termio.NewProgressList(
		os.Stderr,
		fmt.Sprintf("Upgrading cluster %s to %s", cluster, target),
	)
	progress.Start()
	defer progress.Stop()

	states := map[string]string{}
	for {
		response, err := iapp.Call(ctx, client, k8sapi.Status, util.Empty{})
		if err != nil {
			if !k8sWaitSleep(ctx) {
				return k8sUpgradeWaitTimeoutError(cluster)
			}
			continue
		}

		if err := k8sClusterFailure(response, cluster); err != nil {
			return err
		}

		upgrade := response.RollingUpgrade
		if upgrade == nil {
			if !k8sWaitSleep(ctx) {
				return k8sUpgradeWaitTimeoutError(cluster)
			}
			continue
		}

		if uid != "" && upgrade.Uid != "" && upgrade.Uid != uid {
			if !k8sWaitSleep(ctx) {
				return k8sUpgradeWaitTimeoutError(cluster)
			}
			continue
		}

		for _, node := range upgrade.Nodes {
			phase := node.Phase
			if phase == "" {
				phase = k8sapi.MaintenancePhasePending
			}
			if states[node.Name] != phase {
				states[node.Name] = phase
				progress.SetItem(termio.ProgressItem{
					Key:    node.Name,
					Label:  fmt.Sprintf("%s (%s)", node.Name, node.Group),
					Status: k8sUpgradePhaseLabel(phase),
					State:  k8sMaintenanceState(phase),
				})
			}
		}

		switch upgrade.Phase {
		case k8sapi.MaintenancePhaseCompleted:
			progress.Summary(fmt.Sprintf("Upgrade to %s completed", upgrade.TargetRelease))
			return nil

		case k8sapi.MaintenancePhaseFailed,
			k8sapi.MaintenancePhaseBlocked,
			k8sapi.MaintenancePhaseCancelled:
			reason := upgrade.Error
			if reason == "" {
				reason = "the rolling upgrade failed"
			}
			return fmt.Errorf(
				"the rolling upgrade failed: %s; use the dashboard at %s",
				reason,
				k8sValueOrDash(k8sDashboardURL(cluster)),
			)
		}

		if !k8sWaitSleep(ctx) {
			return k8sUpgradeWaitTimeoutError(cluster)
		}
	}
}

func k8sWaitForBackup(client *iapp.Client, cluster string, uid string) error {
	ctx, cancel := context.WithTimeout(context.Background(), k8sBackupWaitTimeout)
	defer cancel()

	client.SetMessageHandler(nil)

	progress := termio.NewProgressList(
		os.Stderr,
		fmt.Sprintf("Waiting for the backup of cluster %s", cluster),
	)
	progress.Start()
	defer progress.Stop()

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
				progress.Summary("Backup completed")
				return nil

			case k8sapi.BackupRequestFailed:
				reason := backup.RequestError
				if reason == "" {
					reason = "the backup failed"
				}
				progress.SetItem(termio.ProgressItem{
					Key:    "backup",
					Status: reason,
					State:  termio.ProgressFailure,
				})
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

func k8sUpgradePhaseLabel(phase string) string {
	switch phase {
	case k8sapi.MaintenancePhasePending:
		return "waiting"
	case k8sapi.MaintenancePhaseRunning:
		return "in progress"
	case k8sapi.MaintenancePhaseCompleted:
		return "completed"
	case k8sapi.MaintenancePhaseFailed, k8sapi.MaintenancePhaseBlocked:
		return "failed"
	case k8sapi.MaintenancePhaseCancelled:
		return "cancelled"
	default:
		return strings.ToLower(phase)
	}
}

func k8sUpgradeWaitTimeoutError(cluster string) error {
	return fmt.Errorf(
		"the rolling upgrade did not finish within %s; use the dashboard at %s",
		k8sUpgradeWaitTimeout,
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

func k8sPrintUpgradeResult(
	cluster string,
	response k8sapi.UpgradeResponse,
	completed bool,
	output string,
) error {
	dashboardUrl := k8sDashboardURL(cluster)

	if output == "json" {
		return k8sPrintJson(k8sUpgradeResult{
			Cluster:       cluster,
			TargetRelease: response.TargetRelease,
			Uid:           response.Uid,
			DashboardUrl:  dashboardUrl,
			Completed:     completed,
		})
	}

	k8sPrintResultSpacer()

	frame := termio.Frame{}
	frame.Title("Cluster " + cluster)
	if completed {
		frame.AppendFieldStyled("Status", termio.Success("Completed"))
	} else {
		frame.AppendFieldStyled("Status", termio.Subtle("Started"))
	}
	frame.AppendField("Target", response.TargetRelease)
	if response.Uid != "" {
		frame.AppendField("Upgrade", response.Uid)
	}
	frame.AppendField("Dashboard", k8sValueOrDash(dashboardUrl))
	frame.Print()
	return nil
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

	k8sPrintResultSpacer()

	frame := termio.Frame{}
	frame.Title("Cluster " + cluster)
	if completed {
		frame.AppendFieldStyled("Status", termio.Success("Completed"))
	} else {
		frame.AppendFieldStyled("Status", termio.Subtle("Started"))
	}
	frame.AppendField("Backup", response.Uid)
	frame.AppendField("Phase", phase)
	frame.AppendField("Dashboard", k8sValueOrDash(dashboardUrl))
	frame.Print()
	return nil
}
