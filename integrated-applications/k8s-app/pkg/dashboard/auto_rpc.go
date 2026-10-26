package dashboard

import (
	"context"
	"fmt"
	"strings"
	"time"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
)

func (app *stackUiApp) autoResponse() (k8sapi.AutoResponse, error) {
	status, err := app.autoStatusRead()
	if err != nil {
		return k8sapi.AutoResponse{}, err
	}

	policy := maintenance.AutoDefaultPolicy()
	if status.PolicyFound {
		policy = maintenance.AutoPolicyNormalize(status.Policy)
	}

	policyInfo := k8sapi.AutoPolicyInfo{
		K8sEnabled:          policy.K8s.Enabled,
		OsEnabled:           policy.Os.Enabled,
		RebootPolicy:        policy.Os.RebootPolicy,
		WindowDays:          append([]string(nil), policy.Window.Days...),
		WindowStartHour:     policy.Window.StartHourUtc,
		WindowDurationHours: policy.Window.DurationHours,
		MaxBackupAgeSeconds: int64(policy.K8s.MaxBackupAge / time.Second),
		DeferUntil:          dashboardUnixSeconds(policy.DeferUntil),
	}

	if start, end, ok := maintenance.AutoEffectiveWindow(time.Now().UTC(), policy); ok {
		policyInfo.NextRunAt = start.Unix()
		policyInfo.NextRunEnd = end.Unix()
	}

	stateInfo := k8sapi.AutoStateInfo{
		Found:           status.StateFound,
		LastRun:         k8sapi.AutoRunInfo{},
		OsReports:       []k8sapi.AutoOsReport{},
		OsRecoveryNodes: []string{},
	}

	if status.StateFound {
		state := status.State
		stateInfo.Blocked = maintenance.AutoStateBlocked(state)
		stateInfo.ResumedAt = dashboardUnixSeconds(state.ResumedAt)
		stateInfo.LastRun = autoRunInfo(state.LastRun)
		for _, report := range state.OsReports {
			stateInfo.OsReports = append(stateInfo.OsReports, k8sapi.AutoOsReport{
				NodeName:       report.NodeName,
				Pending:        report.Pending,
				Security:       report.Security,
				RebootRequired: report.RebootRequired,
				CheckedAt:      dashboardUnixSeconds(report.CheckedAt),
				Error:          report.Error,
			})
		}
	}

	nodes, nodeErr := maintenance.AutoOsRecoveryNodes()
	if nodeErr != nil {
		stateInfo.OsRecoveryError = nodeErr.Error()
	} else {
		stateInfo.OsRecoveryNodes = nodes
	}

	return k8sapi.AutoResponse{
		PolicyFound: status.PolicyFound,
		Policy:      policyInfo,
		State:       stateInfo,
		Ok:          true,
	}, nil
}

func (app *stackUiApp) autoPolicyApply(ctx context.Context, request k8sapi.AutoRequest) error {
	client, err := app.clusterStateValueClient()
	if err != nil {
		return err
	}

	return autoPolicyUpdate(ctx, client, func(policy *maintenance.AutoPolicy) error {
		if request.K8sEnabled.Present {
			policy.K8s.Enabled = request.K8sEnabled.Value
		}
		if request.OsEnabled.Present {
			policy.Os.Enabled = request.OsEnabled.Value
		}

		if strings.TrimSpace(request.WindowDay) != "" {
			day := strings.ToLower(strings.TrimSpace(request.WindowDay))
			if !maintenance.AutoDayValid(day) {
				return fmt.Errorf("invalid maintenance day %q; use mon, tue, wed, thu, fri, sat or sun", request.WindowDay)
			}
			policy.Window.Days = []string{day}
		}

		if request.WindowStartHour.Present {
			hour := request.WindowStartHour.Value
			if hour < 0 || hour > 23 {
				return fmt.Errorf("the window start hour must be from 0 to 23")
			}
			policy.Window.StartHourUtc = hour
		}

		return nil
	})
}

func autoRunInfo(run maintenance.AutoRun) k8sapi.AutoRunInfo {
	checks := make([]k8sapi.AutoCheckInfo, 0, len(run.Checks))
	for _, check := range run.Checks {
		checks = append(checks, k8sapi.AutoCheckInfo{
			Name:   check.Name,
			Ok:     check.Ok,
			Detail: check.Detail,
		})
	}

	return k8sapi.AutoRunInfo{
		Uid:           run.Uid,
		Channel:       run.Channel,
		Trigger:       run.Trigger,
		Phase:         run.Phase,
		TargetRelease: run.TargetRelease,
		Error:         run.Error,
		StartedAt:     dashboardUnixSeconds(run.StartedAt),
		FinishedAt:    dashboardUnixSeconds(run.FinishedAt),
		Checks:        checks,
	}
}
