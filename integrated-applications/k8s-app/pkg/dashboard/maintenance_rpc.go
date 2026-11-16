package dashboard

import (
	"context"
	"sort"
	"time"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
)

func dashboardMaintenanceStatusResponse(app *stackUiApp) (k8sapi.MaintenanceStatusResponse, error) {
	status, err := maintenance.ReadStatus()
	if err != nil {
		return k8sapi.MaintenanceStatusResponse{}, err
	}

	snapshot, snapshotErr := maintenance.Snapshot()
	if snapshotErr != nil {
		snapshot = map[string]maintenance.Operation{}
	}

	response := k8sapi.MaintenanceStatusResponse{
		PolicyFound:     status.PolicyFound,
		Policy:          dashboardMaintenancePolicyInfo(status.Policy),
		Schedule:        dashboardMaintenanceScheduleInfo(status.Schedule, status.Policy),
		ActiveRuns:      []k8sapi.MaintenanceRunInfo{},
		Operations:      []k8sapi.MaintenanceOperation{},
		OperationsError: "",
	}
	if snapshotErr != nil {
		response.OperationsError = snapshotErr.Error()
	}

	for _, run := range status.ActiveRuns {
		response.ActiveRuns = append(response.ActiveRuns, dashboardMaintenanceRunInfo(run, snapshot))
	}
	for _, run := range status.RecentRuns {
		response.RecentRuns = append(response.RecentRuns, dashboardMaintenanceRunInfo(run, snapshot))
	}
	if status.LastRun != nil {
		lastRun := dashboardMaintenanceRunInfo(*status.LastRun, snapshot)
		response.LastRun = &lastRun
	}

	for _, operation := range snapshot {
		response.Operations = append(response.Operations, k8sapi.MaintenanceOperation{
			Uid:      operation.Uid,
			NodeName: operation.NodeName,
			Kind:     operation.Kind,
			Phase:    operation.Phase,
			Error:    operation.Error,
		})
	}
	sort.Slice(response.Operations, func(i int, j int) bool {
		return response.Operations[i].NodeName < response.Operations[j].NodeName
	})

	backupInfo, backupErr := dashboardBackupInfo(app)
	if backupErr != nil {
		response.BackupError = backupErr.Error()
	} else {
		response.Backup = backupInfo
	}

	return response, nil
}

func dashboardMaintenancePolicyInfo(policy maintenance.Policy) k8sapi.MaintenancePolicyInfo {
	return k8sapi.MaintenancePolicyInfo{
		K8sEnabled:          policy.Kubernetes.Enabled,
		OsEnabled:           policy.Os.Enabled,
		WindowDay:           policy.Window.Day,
		WindowStartHour:     policy.Window.StartHourUtc,
		WindowDurationHours: maintenance.WindowDurationHours,
		MaxBackupAgeSeconds: int64(policy.Kubernetes.MaxBackupAge / time.Second),
	}
}

func dashboardMaintenanceScheduleInfo(
	schedule maintenance.ScheduleState,
	policy maintenance.Policy,
) k8sapi.MaintenanceScheduleInfo {
	info := k8sapi.MaintenanceScheduleInfo{
		LastWindowStart: dashboardUnixSeconds(schedule.LastWindowStart),
	}

	now := time.Now().UTC()
	if start, end, ok := maintenance.NextWindow(now, policy.Window); ok {
		info.NextWindowAt = start.Unix()
		info.NextWindowEnd = end.Unix()
	}
	return info
}

func dashboardMaintenanceRunInfo(
	run maintenance.MaintenanceRun,
	snapshot map[string]maintenance.Operation,
) k8sapi.MaintenanceRunInfo {
	info := k8sapi.MaintenanceRunInfo{
		MaintenanceRunSummary: k8sapi.MaintenanceRunSummary{
			Uid:             run.Uid,
			Kind:            run.Kind,
			Trigger:         run.Trigger,
			RequestedBy:     run.RequestedBy,
			RequestedAt:     dashboardUnixSeconds(run.RequestedAt),
			Phase:           run.Phase,
			Message:         run.Message,
			Error:           run.Error,
			CancelRequested: run.CancelRequested,
			StartedAt:       dashboardUnixSeconds(run.StartedAt),
			FinishedAt:      dashboardUnixSeconds(run.FinishedAt),
		},
		Tasks:  []k8sapi.MaintenanceTaskInfo{},
		Checks: []k8sapi.MaintenanceCheckInfo{},
	}

	for _, task := range run.Tasks {
		taskInfo := k8sapi.MaintenanceTaskInfo{
			Kind:          task.Kind,
			Phase:         task.Phase,
			TargetRelease: task.TargetRelease,
			Items:         []k8sapi.MaintenanceItemInfo{},
		}
		for _, item := range task.Items {
			itemInfo := k8sapi.MaintenanceItemInfo{
				Node:         item.NodeName,
				Group:        item.Group,
				Phase:        item.Phase,
				OperationUid: item.OperationUid,
				Error:        item.Error,
			}
			if operation, found := snapshot[item.NodeName]; found && operation.Uid == item.OperationUid {
				itemInfo.Percent = operation.Progress.Percent
				itemInfo.Stage = operation.Progress.Stage
			}
			taskInfo.Items = append(taskInfo.Items, itemInfo)
		}
		info.Tasks = append(info.Tasks, taskInfo)
	}

	for _, check := range run.Checks {
		info.Checks = append(info.Checks, k8sapi.MaintenanceCheckInfo{
			Name:   check.Name,
			Ok:     check.Ok,
			Detail: check.Detail,
		})
	}
	return info
}

func dashboardBackupInfo(app *stackUiApp) (*k8sapi.StatusBackup, error) {
	status, err := app.backupStatusRead()
	if err != nil {
		return nil, err
	}

	info := &k8sapi.StatusBackup{
		StateFound:   status.StateFound,
		RequestFound: status.RequestFound,
	}
	if status.StateFound {
		info.ClaimUid = status.State.ClaimUid
		info.ClaimRequestUid = status.State.ClaimRequestUid
		info.LastAttemptPhase = status.State.LastAttempt.Phase
		info.LastAttemptError = status.State.LastAttempt.Error
		info.LastAttemptFinishedAt = dashboardUnixSeconds(status.State.LastAttempt.FinishedAt)
		info.LastSuccessFinishedAt = dashboardUnixSeconds(status.State.LastSuccess.FinishedAt)
		info.NextDueAt = dashboardUnixSeconds(status.State.DueAt)
		info.Retained = len(status.State.Retained)
	}
	if status.RequestFound {
		info.RequestUid = status.Request.Uid
		info.RequestPhase = status.Request.Phase
		info.RequestError = status.Request.Error
	}
	return info, nil
}

func dashboardMaintenanceRunRequest(request k8sapi.MaintenanceRunRequest) maintenance.RunRequest {
	requestedBy := request.RequestedBy
	if requestedBy == "" {
		requestedBy = "dashboard"
	}
	result := maintenance.RunRequest{
		Tasks: []maintenance.TaskRequest{},
		Target: maintenance.Target{
			All:   request.Target.All,
			Pools: append([]string(nil), request.Target.Pools...),
			Nodes: append([]string(nil), request.Target.Nodes...),
		},
		MaxParallelPerGroup: request.MaxParallelPerGroup,
		RequestedBy:         requestedBy,
	}
	for _, task := range request.Tasks {
		result.Tasks = append(result.Tasks, maintenance.TaskRequest{
			Kind:                    task.Kind,
			TargetRelease:           task.TargetRelease,
			Preparation:             task.Preparation,
			TimeoutSeconds:          task.TimeoutSeconds,
			DeleteVolatilePods:      task.DeleteVolatilePods,
			BypassDisruptionBudgets: task.BypassDisruptionBudgets,
			ForceDelete:             task.ForceDelete,
		})
	}
	return result
}

func registerMaintenanceRpcHandlers(app *stackUiApp, session *ucx.Session) {
	k8sapi.MaintenanceStatus.Handler(session, func(_ context.Context, _ util.Empty) (k8sapi.MaintenanceStatusResponse, error) {
		return dashboardMaintenanceStatusResponse(app)
	})

	k8sapi.MaintenanceRun.Handler(session, func(_ context.Context, request k8sapi.MaintenanceRunRequest) (k8sapi.MaintenanceRunResponse, error) {
		internal := dashboardMaintenanceRunRequest(request)
		var run maintenance.MaintenanceRun
		var err error
		if len(internal.Tasks) == 0 {
			run, err = maintenance.SubmitConfiguredRun(internal.RequestedBy)
		} else {
			run, err = maintenance.SubmitRun(internal, internal.RequestedBy)
		}
		if err != nil {
			return k8sapi.MaintenanceRunResponse{}, err
		}
		return k8sapi.MaintenanceRunResponse{Uid: run.Uid}, nil
	})

	k8sapi.MaintenanceCancel.Handler(session, func(_ context.Context, request k8sapi.MaintenanceCancelRequest) (k8sapi.MaintenanceRunResponse, error) {
		run, err := maintenance.CancelRun(request.RunUid, request.Node)
		if err != nil {
			return k8sapi.MaintenanceRunResponse{}, err
		}
		return k8sapi.MaintenanceRunResponse{Uid: run.Uid}, nil
	})

	k8sapi.MaintenanceRetry.Handler(session, func(_ context.Context, request k8sapi.MaintenanceRetryRequest) (k8sapi.MaintenanceRunResponse, error) {
		run, err := maintenance.RetryRun(request.RunUid, request.Node)
		if err != nil {
			return k8sapi.MaintenanceRunResponse{}, err
		}
		return k8sapi.MaintenanceRunResponse{Uid: run.Uid}, nil
	})

	k8sapi.MaintenancePolicyUpdate.Handler(session, func(_ context.Context, request k8sapi.MaintenancePolicyUpdateRequest) (k8sapi.MaintenanceStatusResponse, error) {
		err := maintenance.ApplyPolicyUpdate(maintenance.PolicyPatch{
			K8sEnabled:      request.K8sEnabled,
			OsEnabled:       request.OsEnabled,
			WindowDay:       request.WindowDay,
			WindowStartHour: request.WindowStartHour,
		})
		if err != nil {
			return k8sapi.MaintenanceStatusResponse{}, err
		}
		return dashboardMaintenanceStatusResponse(app)
	})

	k8sapi.MaintenanceBackup.Handler(session, func(_ context.Context, _ util.Empty) (k8sapi.BackupResponse, error) {
		uid, err := backupRequestSubmitUid(app)
		if err != nil {
			return k8sapi.BackupResponse{}, err
		}
		return k8sapi.BackupResponse{Uid: uid, Phase: k8sapi.BackupRequestPending}, nil
	})
}
