package maintenance

import (
	"context"
	"encoding/json"
	"errors"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

func AutoTestWindowOpen(now time.Time, policy AutoPolicy) bool {
	return !policy.TestWindowStart.IsZero() && !now.Before(policy.TestWindowStart) && now.Before(policy.TestWindowEnd)
}

func autoPolicyWindowOpenNow(now time.Time, policy AutoPolicy) (time.Time, time.Time, bool) {
	if AutoTestWindowOpen(now, policy) {
		return policy.TestWindowStart.UTC(), policy.TestWindowEnd.UTC(), true
	}
	return autoWindowOpenNow(now, policy.Window)
}

func AutoOpenWindowNow(ctx context.Context, kubeconfigPath string) error {
	record, err := readClusterRecord()
	if err != nil {
		return err
	}
	if record.Phase != "created" {
		return errors.New("the cluster is not ready for maintenance")
	}

	client, err := stackClientNew()
	if err != nil {
		return err
	}
	policyRecord, err := autoPolicyRead(client)
	if err != nil {
		return err
	}
	stateRecord, err := autoStateRead(client)
	if err != nil {
		return err
	}

	now := time.Now().UTC()
	if autoStateClaimActive(stateRecord.Value, now) || AutoRunActive(stateRecord.Value.LastRun) {
		return errors.New("another automatic maintenance action is in progress")
	}
	if AutoStateBlocked(stateRecord.Value) {
		return errors.New("automatic maintenance is stopped; resolve the problem and resume automatic maintenance")
	}

	policy := AutoDefaultPolicy()
	if policyRecord.Found {
		policy = AutoPolicyNormalize(policyRecord.Value)
	}
	if !policy.K8s.Enabled && !policy.Os.Enabled {
		return errors.New("enable Kubernetes or operating-system updates before starting maintenance")
	}
	policy.TestWindowStart = now
	policy.TestWindowEnd = now.Add(AutoWindowDurationHours * time.Hour)
	value, err := json.Marshal(policy)
	if err != nil {
		return err
	}
	_, err = shared.RpcGrantStateWriteChecked(
		client,
		AutoPolicyKey,
		value,
		policyRecord.Revision,
		[]ucxapi.StackStateRevisionCondition{{
			Key:              AutoStateKey,
			ExpectedRevision: stateRecord.Revision,
		}},
		nil,
		util.OptNone[int64](),
	)
	if shared.IsConflict(err) {
		return errors.New("automatic maintenance changed while starting maintenance; please try again")
	}
	if err != nil {
		return err
	}

	return autoUpgradeSweep(ctx, kubeconfigPath, client)
}

func AutoDeferUntil(until time.Time) error {
	client, err := stackClientNew()
	if err != nil {
		return err
	}
	record, err := autoPolicyRead(client)
	if err != nil {
		return err
	}
	policy := record.Value
	if !record.Found {
		policy = AutoDefaultPolicy()
	}
	policy.DeferUntil = until.UTC()
	value, err := json.Marshal(policy)
	if err != nil {
		return err
	}
	_, err = shared.RpcGrantStateWrite(client, AutoPolicyKey, value, record.Revision)
	if shared.IsConflict(err) {
		return errors.New("the maintenance schedule changed; please try again")
	}
	return err
}
