package recovery

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

func (app *app) registerRpcHandlers() {
	session := *app.Session()

	k8sapi.RecoveryStatus.Handler(session, func(_ context.Context, _ util.Empty) (k8sapi.RecoveryStatusResponse, error) {
		load, mountPath := app.recoveryRpcLoad()
		return recoveryStatusBuild(load, mountPath, true), nil
	})

	k8sapi.RecoveryStart.Handler(session, func(ctx context.Context, request k8sapi.RecoveryStartRequest) (k8sapi.RecoveryStartResponse, error) {
		return app.recoveryRpcStart(ctx, request)
	})
}

func (app *app) recoveryRpcLoad() (recoveryLoadResult, string) {
	app.mu.Lock()
	stackId := app.stackId()
	mountPath := app.MountPath
	app.mu.Unlock()

	return recoveryLoad(*app.Session(), stackId, mountPath), mountPath
}

func recoveryStatusBuild(
	load recoveryLoadResult,
	mountPath string,
	includeChecks bool,
) k8sapi.RecoveryStatusResponse {
	response := k8sapi.RecoveryStatusResponse{
		Supported:    true,
		ClusterFound: load.ClusterFound,
		Backups:      []k8sapi.RecoveryBackupInfo{},
		Checks:       []k8sapi.RecoveryCheckInfo{},
		Messages:     append([]string{}, load.Messages...),
	}

	if load.ClusterFound {
		response.ClusterPhase = load.Cluster.Phase
	}

	for _, entry := range load.Backups {
		response.Backups = append(response.Backups, k8sapi.RecoveryBackupInfo{
			Id:            entry.Id,
			Release:       entry.Release,
			CreatedAt:     entry.CreatedAt.Unix(),
			SizeBytes:     entry.SizeBytes,
			CreatedByNode: entry.CreatedByNode,
		})
	}

	selectedBackupId := ""
	if load.RecoveryFound {
		record := load.Recovery
		response.RecoveryFound = true
		response.Active = record.Phase == shared.ClusterRecoveryPhaseActive
		response.Phase = record.Phase
		response.Stage = record.Stage
		response.OperationUid = record.OperationUid
		response.BackupId = record.BackupId
		response.BackupRelease = record.BackupRelease
		response.StartedAt = recoveryUnixSeconds(record.StartedAt)
		response.StartedBy = record.StartedBy
		response.ClosedAt = recoveryUnixSeconds(record.ClosedAt)
		response.LastError = record.LastError
		if response.Active && record.BackupId != "" {
			selectedBackupId = record.BackupId
		}
	}

	if selectedBackupId == "" && len(load.Backups) > 0 {
		selectedBackupId = load.Backups[0].Id
	}
	response.SelectedBackupId = selectedBackupId

	for _, entry := range load.Backups {
		if entry.Id == selectedBackupId {
			response.CredentialsStatus = recoveryCredentialsString(recoveryCheckCredentials(mountPath, entry))
			break
		}
	}

	if includeChecks && response.Active && response.Stage == shared.ClusterRecoveryStageVerify && load.ClusterFound {
		for _, result := range recoveryApiChecks(mountPath, load.Cluster, load.Recovery) {
			response.Checks = append(response.Checks, recoveryCheckInfo(result))
		}
	}

	return response
}

func (app *app) recoveryRpcStart(
	ctx context.Context,
	request k8sapi.RecoveryStartRequest,
) (k8sapi.RecoveryStartResponse, error) {
	load, mountPath := app.recoveryRpcLoad()
	if !load.ClusterFound {
		return k8sapi.RecoveryStartResponse{}, recoveryLoadError(load)
	}

	entry, err := recoveryChooseBackup(load, request.BackupId)
	if err != nil {
		return k8sapi.RecoveryStartResponse{}, err
	}

	if _, known := shared.ReleaseByExactVersion(entry.Release); !known {
		return k8sapi.RecoveryStartResponse{}, fmt.Errorf(
			"the backup %s uses the unavailable release %s",
			entry.Id,
			entry.Release,
		)
	}

	if entry.ServerTokenHash == "" || entry.AgentTokenHash == "" {
		return k8sapi.RecoveryStartResponse{}, fmt.Errorf(
			"the metadata of backup %s is incomplete",
			entry.Id,
		)
	}

	switch recoveryCheckCredentials(mountPath, entry) {
	case recoveryCredentialsUnchecked:
		return k8sapi.RecoveryStartResponse{}, errors.New("the live cluster credentials could not be read")

	case recoveryCredentialsMismatch:
		if !request.AcknowledgeRisk {
			return k8sapi.RecoveryStartResponse{}, errors.New(
				"the backup credentials differ from the cluster credentials; use --acknowledge-risk to continue",
			)
		}
	}

	active := load.RecoveryFound && load.Recovery.Phase == shared.ClusterRecoveryPhaseActive
	stage := shared.ClusterRecoveryStageStop
	if active && load.Recovery.Stage != "" {
		stage = load.Recovery.Stage
	}

	if active && load.Recovery.BackupId != "" && load.Recovery.BackupId != entry.Id {
		allowed := stage == shared.ClusterRecoveryStageStop ||
			(stage == shared.ClusterRecoveryStageRecreate && recoveryCanChangeBackup(mountPath, load.Cluster))
		if !allowed {
			return k8sapi.RecoveryStartResponse{}, errors.New(
				"the recovery has reached the restore step and the backup cannot change",
			)
		}
	}

	session := *app.Session()
	progress := func(message string) {
		ucxsvc.UiSendProgress(app, message)
	}

	switch {
	case active && stage == shared.ClusterRecoveryStageRestore:
		return app.recoveryRestoreResume(load, mountPath, progress)

	case active && stage == shared.ClusterRecoveryStageVerify:
		return app.recoveryVerification(load, mountPath), nil

	case active && stage == shared.ClusterRecoveryStageRecreate && load.Recovery.BackupId == entry.Id:
		progress("Resuming the recovery")
		if _, err := app.recreateWork(session, progress); err != nil {
			return k8sapi.RecoveryStartResponse{}, err
		}

	default:
		if err := ctx.Err(); err != nil {
			return k8sapi.RecoveryStartResponse{}, err
		}
		if _, err := app.beginRecoveryWork(session, entry, progress); err != nil {
			return k8sapi.RecoveryStartResponse{}, err
		}
		if err := ctx.Err(); err != nil {
			return k8sapi.RecoveryStartResponse{}, err
		}
		if _, err := app.stopWork(session, progress); err != nil {
			return k8sapi.RecoveryStartResponse{}, err
		}
		if err := ctx.Err(); err != nil {
			return k8sapi.RecoveryStartResponse{}, err
		}
		if _, err := app.recreateWork(session, progress); err != nil {
			return k8sapi.RecoveryStartResponse{}, err
		}
	}

	return k8sapi.RecoveryStartResponse{
		Phase:    shared.ClusterRecoveryPhaseActive,
		Stage:    shared.ClusterRecoveryStageRestore,
		BackupId: entry.Id,
		Message:  "The replacement nodes were created. The cluster is restoring from the backup.",
		Checks:   []k8sapi.RecoveryCheckInfo{},
	}, nil
}

func (app *app) recoveryRestoreResume(
	load recoveryLoadResult,
	mountPath string,
	progress func(string),
) (k8sapi.RecoveryStartResponse, error) {
	progress("Waiting for the cluster to respond")

	if err := recoveryApiProbe(mountPath, load.Cluster); err != nil {
		return k8sapi.RecoveryStartResponse{
			Phase:    shared.ClusterRecoveryPhaseActive,
			Stage:    shared.ClusterRecoveryStageRestore,
			BackupId: load.Recovery.BackupId,
			Message:  "The cluster has not responded yet.",
			Checks:   []k8sapi.RecoveryCheckInfo{},
		}, nil
	}

	client, err := shared.ClusterStateClientNewSession(*app.Session(), app.stackId())
	if err != nil {
		return k8sapi.RecoveryStartResponse{}, err
	}

	if err := recoverySetStage(client, shared.ClusterRecoveryStageVerify); err != nil {
		return k8sapi.RecoveryStartResponse{}, err
	}

	load.Recovery.Stage = shared.ClusterRecoveryStageVerify
	return app.recoveryVerification(load, mountPath), nil
}

func (app *app) recoveryVerification(load recoveryLoadResult, mountPath string) k8sapi.RecoveryStartResponse {
	response := k8sapi.RecoveryStartResponse{
		Phase:    shared.ClusterRecoveryPhaseActive,
		Stage:    shared.ClusterRecoveryStageVerify,
		BackupId: load.Recovery.BackupId,
		Message:  "The recovery reached verification. Review the checks and close recovery in the dashboard.",
		Checks:   []k8sapi.RecoveryCheckInfo{},
	}

	failed := 0
	for _, result := range recoveryApiChecks(mountPath, load.Cluster, load.Recovery) {
		response.Checks = append(response.Checks, recoveryCheckInfo(result))
		if !result.Ok {
			failed++
		}
	}

	if failed > 0 {
		response.Message = fmt.Sprintf(
			"The recovery reached verification with %d checks that need attention.",
			failed,
		)
	}

	return response
}

func recoveryChooseBackup(load recoveryLoadResult, requested string) (backupEntry, error) {
	requested = strings.TrimSpace(requested)

	if requested != "" {
		for _, entry := range load.Backups {
			if entry.Id == requested {
				return entry, nil
			}
		}
		return backupEntry{}, fmt.Errorf("the backup %s was not found on the state drive", requested)
	}

	if load.RecoveryFound && load.Recovery.Phase == shared.ClusterRecoveryPhaseActive &&
		load.Recovery.BackupId != "" {
		for _, entry := range load.Backups {
			if entry.Id == load.Recovery.BackupId {
				return entry, nil
			}
		}
		return backupEntry{}, fmt.Errorf(
			"the recovery backup %s is not available on the state drive",
			load.Recovery.BackupId,
		)
	}

	if len(load.Backups) > 0 {
		return load.Backups[0], nil
	}

	return backupEntry{}, errors.New("no backups were found on the state drive")
}

func recoveryLoadError(load recoveryLoadResult) error {
	if len(load.Messages) > 0 {
		return errors.New(strings.Join(load.Messages, " "))
	}
	return errors.New("the cluster record could not be read")
}

func recoveryCheckInfo(result recoveryCheckResult) k8sapi.RecoveryCheckInfo {
	return k8sapi.RecoveryCheckInfo{
		Name:   result.Name,
		Status: result.Status,
		Detail: result.Detail,
		Ok:     result.Ok,
	}
}

func recoveryCredentialsString(status recoveryCredentialsStatus) string {
	switch status {
	case recoveryCredentialsOk:
		return k8sapi.RecoveryCredentialsOk
	case recoveryCredentialsMismatch:
		return k8sapi.RecoveryCredentialsMismatch
	default:
		return k8sapi.RecoveryCredentialsUnchecked
	}
}

func recoveryUnixSeconds(value time.Time) int64 {
	if value.IsZero() {
		return 0
	}
	return value.Unix()
}
