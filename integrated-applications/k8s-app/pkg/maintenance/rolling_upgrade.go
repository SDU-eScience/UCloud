package maintenance

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/tools/clientcmd"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

const rollingUpgradeKey = "maintenance/rolling-upgrade"

type RollingUpgradeNode struct {
	Name         string
	Uid          string
	Group        string
	OperationUid string
	Revision     int64
	Phase        string
}

type RollingUpgrade struct {
	Uid                 string
	TargetRelease       string
	Trigger             string
	AutoRunUid          string
	Phase               string
	Error               string
	StartedAt           time.Time
	UpdatedAt           time.Time
	CoordinatorUid      string
	CoordinatorDeadline time.Time
	Groups              map[string]Options
	Nodes               []RollingUpgradeNode
}

func rollingUpgradeRead(client stackClient) (RollingUpgrade, int64, bool, error) {
	record, err := stackValueRead[RollingUpgrade](client, rollingUpgradeKey, "rolling upgrade")
	if err != nil {
		return RollingUpgrade{}, 0, false, err
	}
	return record.Value, record.Revision, record.Found, nil
}

func RollingUpgradeSnapshot() (RollingUpgrade, int64, bool, error) {
	client, err := stackClientNew()
	if err != nil {
		return RollingUpgrade{}, 0, false, err
	}
	return rollingUpgradeRead(client)
}

func rollingUpgradeWrite(client stackClient, upgrade RollingUpgrade, revision int64) error {
	_, err := rollingUpgradeWriteChecked(client, upgrade, revision, nil, util.OptNone[int64]())
	return err
}

func rollingUpgradeWriteChecked(
	client stackClient,
	upgrade RollingUpgrade,
	revision int64,
	conditions []ucxapi.StackStateRevisionCondition,
	validUntil util.Option[int64],
) (int64, error) {
	upgrade.UpdatedAt = time.Now().UTC()
	value, err := json.Marshal(upgrade)
	if err != nil {
		return 0, err
	}
	return shared.RpcGrantStateWriteChecked(client, rollingUpgradeKey, value, revision, conditions, nil, validUntil)
}

func RollingUpgradeStart(ctx context.Context, kubeconfigPath string, release string, groups map[string]Options) error {
	return rollingUpgradeStart(ctx, kubeconfigPath, release, groups, rollingUpgradeStartOptions{trigger: AutoTriggerManual})
}

type rollingUpgradeStartOptions struct {
	trigger    string
	autoRunUid string
	conditions []ucxapi.StackStateRevisionCondition
	validUntil util.Option[int64]
}

func rollingUpgradeStart(
	ctx context.Context,
	kubeconfigPath string,
	release string,
	groups map[string]Options,
	start rollingUpgradeStartOptions,
) error {
	_, known := shared.ReleaseByExactVersion(release)
	if !known {
		return errors.New("select a supported target release")
	}
	record, found, err := readClusterRecord()
	if err != nil {
		return err
	}
	if !found {
		return errors.New("the cluster record does not exist")
	}
	if record.Phase != "created" || len(record.Nodes) == 0 {
		return errors.New("the cluster is not ready for a rolling upgrade")
	}
	if err := recoveryIntentGuard(); err != nil {
		return err
	}
	client, err := stackClientNew()
	if err != nil {
		return err
	}
	existing, revision, _, err := rollingUpgradeRead(client)
	if err != nil {
		return err
	}
	if PhaseActive(existing.Phase) {
		return errors.New("a rolling upgrade is already active")
	}
	config, err := clientcmd.BuildConfigFromFlags("", kubeconfigPath)
	if err != nil {
		return err
	}
	clientset, err := kubernetes.NewForConfig(config)
	if err != nil {
		return err
	}
	listed, err := clientset.CoreV1().Nodes().List(ctx, metav1.ListOptions{})
	if err != nil {
		return err
	}
	operations, err := rollingUpgradeNodeRecords(ctx, client)
	if err != nil {
		return err
	}
	upgrade := RollingUpgrade{
		Uid:           shared.NewUid(),
		TargetRelease: release,
		Trigger:       start.trigger,
		AutoRunUid:    start.autoRunUid,
		Phase:         PhaseRunning,
		StartedAt:     time.Now().UTC(),
		Groups:        groups,
	}
	for _, recorded := range record.Nodes {
		options, present := groups[recorded.Group]
		if !present {
			return fmt.Errorf("select upgrade preparation for node group %s", recorded.Group)
		}
		optionsErr := validateOptions(options)
		if optionsErr != nil {
			return optionsErr
		}
		if options.ForceDelete {
			return errors.New("force delete cannot be used with rolling upgrades")
		}
		operation := operations[recorded.Hostname]
		if PhaseActive(operation.Value.Phase) || operationBlockedByRecovery(operation.Value) || operation.Value.HostTerminationUnverified {
			return fmt.Errorf("node %s has active maintenance or requires recovery", recorded.Hostname)
		}
		node := RollingUpgradeNode{
			Name:         recorded.Hostname,
			Group:        recorded.Group,
			OperationUid: shared.NewUid(),
			Revision:     operation.Revision,
			Phase:        PhasePending,
		}
		for i := range listed.Items {
			live := &listed.Items[i]
			if live.Name != recorded.Hostname {
				continue
			}
			if !nodeIsReady(live) {
				return fmt.Errorf("node %s is not ready", live.Name)
			}
			current := live.Status.NodeInfo.KubeletVersion
			if current == release {
				node.Phase = PhaseCompleted
			} else if !shared.NodeAgentUpgradeAllowed(current, release) {
				return fmt.Errorf("node %s cannot upgrade from %s to %s", live.Name, current, release)
			}
			node.Uid = string(live.UID)
			break
		}
		if node.Uid == "" {
			return fmt.Errorf("node %s is missing from Kubernetes", recorded.Hostname)
		}
		upgrade.Nodes = append(upgrade.Nodes, node)
	}
	if ctx.Err() != nil {
		return ctx.Err()
	}
	conditions := rollingUpgradeNodeConditions(upgrade, operations, "")
	conditions = append(conditions, start.conditions...)
	if start.trigger == AutoTriggerWindow {
		check, checkErr := autoUpgradeScheduleCheck(client, time.Now().UTC())
		if checkErr != nil {
			return checkErr
		}
		if !check.open {
			return errAutoSchedulePaused
		}
		conditions = append(conditions, check.condition)
		deadline := check.deadline.UnixMilli()
		start.validUntil = util.OptValue(min(start.validUntil.GetOrDefault(deadline), deadline))
	}
	_, err = rollingUpgradeWriteChecked(client, upgrade, revision, conditions, start.validUntil)
	return err
}

func rollingUpgradeNodeGuard(client stackClient, nodeName string) (ucxapi.StackStateRevisionCondition, error) {
	upgrade, revision, _, err := rollingUpgradeRead(client)
	if err != nil {
		return ucxapi.StackStateRevisionCondition{}, err
	}

	condition := ucxapi.StackStateRevisionCondition{
		Key:              rollingUpgradeKey,
		ExpectedRevision: revision,
	}
	if !PhaseActive(upgrade.Phase) {
		return condition, nil
	}
	for _, node := range upgrade.Nodes {
		if node.Name == nodeName {
			return condition, errors.New("a rolling upgrade is active. Wait for it to finish before starting node maintenance")
		}
	}
	return condition, nil
}

func rollingUpgradeNodeRecords(ctx context.Context, client stackClient) (map[string]stackRecord, error) {
	result := map[string]stackRecord{}
	next := util.OptNone[string]()
	for {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		page, err := shared.RpcGrantStateList(client, stackKeyPrefix, next, stackListPageSize)
		if err != nil {
			return nil, err
		}
		for _, record := range page.Items {
			state, decodeErr := stackValueDecode[Operation](record, "maintenance record "+record.Key)
			if decodeErr != nil {
				return nil, decodeErr
			}
			result[strings.TrimPrefix(record.Key, stackKeyPrefix)] = state
		}
		if !page.Next.Present {
			return result, nil
		}
		next = page.Next
	}
}

func rollingUpgradeNodeConditions(upgrade RollingUpgrade, records map[string]stackRecord, skipNode string) []ucxapi.StackStateRevisionCondition {
	conditions := make([]ucxapi.StackStateRevisionCondition, 0, len(upgrade.Nodes))
	for _, node := range upgrade.Nodes {
		if node.Name == skipNode {
			continue
		}
		conditions = append(conditions, ucxapi.StackStateRevisionCondition{
			Key:              stackNodeKey(node.Name),
			ExpectedRevision: records[node.Name].Revision,
		})
	}
	return conditions
}

func rollingUpgradeSubmissionConditions(
	upgrade RollingUpgrade,
	records map[string]stackRecord,
	skipNode string,
) ([]ucxapi.StackStateRevisionCondition, []ucxapi.StackStateValueCondition) {
	revisions := []ucxapi.StackStateRevisionCondition{}
	values := []ucxapi.StackStateValueCondition{}
	release, _ := json.Marshal(upgrade.TargetRelease)
	for _, node := range upgrade.Nodes {
		if node.Name == skipNode {
			continue
		}
		record := records[node.Name]
		operation := record.Value
		submitted := operation.Uid == node.OperationUid && operation.Kind == KindUpgrade &&
			(PhaseActive(operation.Phase) || operation.Phase == PhaseCompleted)
		if !submitted {
			revisions = append(revisions, ucxapi.StackStateRevisionCondition{
				Key:              stackNodeKey(node.Name),
				ExpectedRevision: record.Revision,
			})
			continue
		}
		phases := []json.RawMessage{
			json.RawMessage(`"Pending"`),
			json.RawMessage(`"Running"`),
			json.RawMessage(`"Completed"`),
		}
		if operation.Phase == PhaseCompleted {
			phases = []json.RawMessage{json.RawMessage(`"Completed"`)}
		}
		uid, _ := json.Marshal(node.OperationUid)
		nodeUid, _ := json.Marshal(node.Uid)
		values = append(values, ucxapi.StackStateValueCondition{
			Key: stackNodeKey(node.Name),
			Fields: map[string][]json.RawMessage{
				"uid":              {uid},
				"nodeUid":          {nodeUid},
				"kind":             {json.RawMessage(`"upgrade"`)},
				"targetRelease":    {release},
				"phase":            phases,
				"cancelRequested":  {json.RawMessage("false")},
				"recoveryRequired": {json.RawMessage("false")},
			},
		})
	}
	return revisions, values
}

func rollingUpgradeSweep(ctx context.Context, client stackClient) error {
	upgrade, revision, _, err := rollingUpgradeRead(client)
	if err != nil || !PhaseActive(upgrade.Phase) {
		return err
	}
	recovery, recoveryErr := recoveryIntentActive()
	if recoveryErr != nil {
		return recoveryErr
	}
	if recovery {
		return nil
	}
	if upgrade.CoordinatorUid != "" && time.Now().Before(upgrade.CoordinatorDeadline) {
		return nil
	}
	records, err := rollingUpgradeNodeRecords(ctx, client)
	if err != nil {
		return err
	}
	upgrade.CoordinatorUid = shared.NewUid()
	upgrade.CoordinatorDeadline = time.Now().Add(staleAfter)
	revision, err = rollingUpgradeWriteChecked(client, upgrade, revision, nil, util.OptNone[int64]())
	if err != nil {
		return err
	}
	defer rollingUpgradeReleaseCoordinator(client, upgrade.CoordinatorUid)
	validUntil := util.OptValue(upgrade.CoordinatorDeadline.UnixMilli())
	changed := false
	controlPlaneDone := true
	allDone := true
	busyGroups := map[string]bool{}
	for i := range upgrade.Nodes {
		node := &upgrade.Nodes[i]
		if node.Phase == PhaseCompleted {
			continue
		}
		record := records[node.Name]
		operation := record.Value
		if operation.Uid == node.OperationUid {
			if node.Phase != operation.Phase {
				node.Phase = operation.Phase
				changed = true
			}
			if !PhaseActive(operation.Phase) && operation.Phase != PhaseCompleted {
				upgrade.Phase = PhaseFailed
				upgrade.Error = fmt.Sprintf("Upgrade of %s stopped: %s", node.Name, operation.Error)
				_, err = rollingUpgradeWriteChecked(client, upgrade, revision, nil, validUntil)
				return err
			}
			if operation.Phase != PhaseCompleted {
				busyGroups[node.Group] = true
			}
		} else if record.Revision != node.Revision {
			upgrade.Phase = PhaseFailed
			upgrade.Error = fmt.Sprintf("Maintenance on %s changed during the rolling upgrade", node.Name)
			_, err = rollingUpgradeWriteChecked(client, upgrade, revision, nil, validUntil)
			return err
		}
		if node.Phase != PhaseCompleted {
			allDone = false
			if node.Group == shared.GroupControlPlane {
				controlPlaneDone = false
			}
		}
	}
	if allDone {
		upgrade.Phase = PhaseCompleted
		_, err = rollingUpgradeWriteChecked(client, upgrade, revision, nil, validUntil)
		return err
	}
	if changed {
		_, err = rollingUpgradeWriteChecked(client, upgrade, revision, nil, validUntil)
		return err
	}
	for _, node := range upgrade.Nodes {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		if node.Phase != PhasePending || busyGroups[node.Group] {
			continue
		}
		if node.Group != shared.GroupControlPlane && !controlPlaneDone {
			continue
		}
		if time.Until(upgrade.CoordinatorDeadline) < staleAfter/2 {
			upgrade.CoordinatorDeadline = time.Now().Add(staleAfter)
			revision, err = rollingUpgradeWriteChecked(client, upgrade, revision, nil, validUntil)
			if err != nil {
				return err
			}
			validUntil = util.OptValue(upgrade.CoordinatorDeadline.UnixMilli())
		}
		busyGroups[node.Group] = true
		record := records[node.Name]
		if record.Value.Uid == node.OperationUid {
			continue
		}
		if record.Revision != node.Revision {
			return nil
		}
		operation := submitRecord(submission{
			nodeName: node.Name,
			nodeUid:  node.Uid,
			kind:     KindUpgrade,
			options:  upgrade.Groups[node.Group],
			release:  upgrade.TargetRelease,
		}, record.Value)
		operation.Uid = node.OperationUid
		value, marshalErr := json.Marshal(operation)
		if marshalErr != nil {
			return marshalErr
		}
		conditions, valueConditions := rollingUpgradeSubmissionConditions(upgrade, records, node.Name)
		conditions = append(conditions, ucxapi.StackStateRevisionCondition{
			Key:              rollingUpgradeKey,
			ExpectedRevision: revision,
		})
		submissionDeadline := validUntil
		if upgrade.Trigger == AutoTriggerWindow {
			check, checkErr := autoUpgradeScheduleCheck(client, time.Now().UTC())
			if checkErr != nil {
				return checkErr
			}
			if !check.open {
				return nil
			}
			conditions = append(conditions, check.condition)
			submissionDeadline = util.OptValue(min(validUntil.Value, check.deadline.UnixMilli()))
		}
		operationRevision, writeErr := shared.RpcGrantStateWriteChecked(
			client,
			stackNodeKey(node.Name),
			value,
			node.Revision,
			conditions,
			valueConditions,
			submissionDeadline,
		)
		if writeErr != nil {
			return writeErr
		}
		records[node.Name] = stackRecord{
			Found:    true,
			Value:    operation,
			Revision: operationRevision,
		}
		resetNodeLog(node.Name, operation.Uid)
	}
	return nil
}

func rollingUpgradeReleaseCoordinator(client stackClient, coordinatorUid string) {
	upgrade, revision, _, err := rollingUpgradeRead(client)
	if err != nil || upgrade.CoordinatorUid != coordinatorUid {
		return
	}
	upgrade.CoordinatorUid = ""
	upgrade.CoordinatorDeadline = time.Time{}
	err = rollingUpgradeWrite(client, upgrade, revision)
	if err != nil && !shared.IsConflict(err) {
		log.Warn("k8s-app rolling upgrade: could not release coordinator: %s", err)
	}
}
