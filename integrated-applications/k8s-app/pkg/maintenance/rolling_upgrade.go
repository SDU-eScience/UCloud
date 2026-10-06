package maintenance

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/tools/clientcmd"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
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
	Phase               string
	Error               string
	StartedAt           time.Time
	UpdatedAt           time.Time
	CoordinatorUid      string
	CoordinatorDeadline time.Time
	Groups              map[string]Options
	Nodes               []RollingUpgradeNode
}

func rollingUpgradeRead(client stackClient) (RollingUpgrade, int64, error) {
	record, found, err := shared.RpcGrantStateRead(client, rollingUpgradeKey)
	if err != nil || !found {
		return RollingUpgrade{}, 0, err
	}
	var upgrade RollingUpgrade
	if !record.IsEmpty() {
		err = json.Unmarshal(record.Value, &upgrade)
	}
	return upgrade, record.Revision, err
}

func RollingUpgradeSnapshot() (RollingUpgrade, error) {
	client, err := stackClientNew()
	if err != nil {
		return RollingUpgrade{}, err
	}
	upgrade, _, err := rollingUpgradeRead(client)
	return upgrade, err
}

func rollingUpgradeWrite(client stackClient, upgrade RollingUpgrade, revision int64) error {
	upgrade.UpdatedAt = time.Now().UTC()
	value, err := json.Marshal(upgrade)
	if err != nil {
		return err
	}
	_, err = stackWrite(client, rollingUpgradeKey, value, revision)
	return err
}

func RollingUpgradeStart(ctx context.Context, kubeconfigPath string, release string, groups map[string]Options) error {
	_, known := shared.ReleaseByExactVersion(release)
	if !known {
		return errors.New("select a supported target release")
	}
	record, err := readClusterRecord()
	if err != nil {
		return err
	}
	if record.Phase != "created" || len(record.Nodes) == 0 {
		return errors.New("the cluster is not ready for a rolling upgrade")
	}
	client, err := stackClientNew()
	if err != nil {
		return err
	}
	existing, revision, err := rollingUpgradeRead(client)
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
	upgrade := RollingUpgrade{
		Uid:           newOperationUid(),
		TargetRelease: release,
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
		operation, readErr := stackRead(client, stackNodeKey(recorded.Hostname))
		if readErr != nil {
			return readErr
		}
		if PhaseActive(operation.Operation.Phase) || upgradeBlockedByRecovery(operation.Operation) || operation.Operation.HostTerminationUnverified {
			return fmt.Errorf("node %s has active maintenance or requires recovery", recorded.Hostname)
		}
		node := RollingUpgradeNode{
			Name:         recorded.Hostname,
			Group:        recorded.Group,
			OperationUid: newOperationUid(),
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
	return rollingUpgradeWrite(client, upgrade, revision)
}

func rollingUpgradeNodeGuard(nodeName string) error {
	upgrade, err := RollingUpgradeSnapshot()
	if err != nil {
		return err
	}
	if !PhaseActive(upgrade.Phase) {
		return nil
	}
	for _, node := range upgrade.Nodes {
		if node.Name == nodeName {
			return errors.New("a rolling upgrade is active; wait for it to finish before starting node maintenance")
		}
	}
	return nil
}

func rollingUpgradeSweep(client stackClient) error {
	upgrade, revision, err := rollingUpgradeRead(client)
	if err != nil || !PhaseActive(upgrade.Phase) {
		return err
	}
	if upgrade.CoordinatorUid != "" && time.Now().Before(upgrade.CoordinatorDeadline) {
		return nil
	}
	upgrade.CoordinatorUid = newOperationUid()
	upgrade.CoordinatorDeadline = time.Now().Add(staleAfter)
	claim, err := json.Marshal(upgrade)
	if err != nil {
		return err
	}
	revision, err = stackWrite(client, rollingUpgradeKey, claim, revision)
	if err != nil {
		return err
	}
	defer rollingUpgradeReleaseCoordinator(client, upgrade.CoordinatorUid)
	changed := false
	controlPlaneDone := true
	allDone := true
	busyGroups := map[string]bool{}
	for i := range upgrade.Nodes {
		node := &upgrade.Nodes[i]
		if node.Phase == PhaseCompleted {
			continue
		}
		record, readErr := stackRead(client, stackNodeKey(node.Name))
		if readErr != nil {
			return readErr
		}
		operation := record.Operation
		if operation.Uid == node.OperationUid {
			if node.Phase != operation.Phase {
				node.Phase = operation.Phase
				changed = true
			}
			if !PhaseActive(operation.Phase) && operation.Phase != PhaseCompleted {
				upgrade.Phase = PhaseFailed
				upgrade.Error = fmt.Sprintf("Upgrade of %s stopped: %s", node.Name, operation.Error)
				return rollingUpgradeWrite(client, upgrade, revision)
			}
			if operation.Phase != PhaseCompleted {
				busyGroups[node.Group] = true
			}
		} else if record.Revision != node.Revision {
			upgrade.Phase = PhaseFailed
			upgrade.Error = fmt.Sprintf("Maintenance on %s changed during the rolling upgrade", node.Name)
			return rollingUpgradeWrite(client, upgrade, revision)
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
		return rollingUpgradeWrite(client, upgrade, revision)
	}
	if changed {
		return rollingUpgradeWrite(client, upgrade, revision)
	}
	for _, node := range upgrade.Nodes {
		if time.Now().After(upgrade.CoordinatorDeadline) {
			return nil
		}
		if node.Phase != PhasePending || busyGroups[node.Group] {
			continue
		}
		if node.Group != shared.GroupControlPlane && !controlPlaneDone {
			continue
		}
		busyGroups[node.Group] = true
		record, readErr := stackRead(client, stackNodeKey(node.Name))
		if readErr != nil {
			return readErr
		}
		if record.Operation.Uid == node.OperationUid {
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
		}, record.Operation)
		operation.Uid = node.OperationUid
		value, marshalErr := json.Marshal(operation)
		if marshalErr != nil {
			return marshalErr
		}
		_, writeErr := stackWrite(client, stackNodeKey(node.Name), value, node.Revision)
		if writeErr != nil {
			return writeErr
		}
		resetNodeLog(node.Name, operation.Uid)
	}
	return nil
}

func rollingUpgradeReleaseCoordinator(client stackClient, coordinatorUid string) {
	upgrade, revision, err := rollingUpgradeRead(client)
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

func rollingUpgradeSweepLogged(client stackClient) {
	err := rollingUpgradeSweep(client)
	if err != nil && !shared.IsConflict(err) {
		log.Warn("k8s-app rolling upgrade: %s", err)
	}
}
