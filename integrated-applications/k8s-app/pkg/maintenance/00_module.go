// Introduction
// =====================================================================================================================
// This file implements the coordinator of the maintenance system of the UCloud Kubernetes stack. The coordinator runs
// on every control plane node of a cluster. The main responsibilities of this system are:
//
// - Recording maintenance operations in the stack state so that any coordinator instance can pick them up
// - Dispatching stale operations to a worker that executes them against the Kubernetes API
// - Exposing a small API for the dashboard to submit and cancel operations
// - Guarding the cluster against operations that would leave it without a working control plane
//
// The coordinator exists because node maintenance must survive restarts of the process that started it. Operations
// are stored in the stack state under a key per node. A sweep picks up any operation whose worker died and runs it
// again from its recorded state.
//
// Operation model
// ---------------------------------------------------------------------------------------------------------------------
// An operation is a record with a kind, a phase, and a deadline. The kind states what the operation does: cordon,
// cordon plus drain, uncordon, or upgrade. The phase states where the operation is: pending, running, or one of the
// terminal phases. The deadline bounds the operation and protects the cluster from a worker that never finishes.
//
// Operations are identified by a uid that is generated at submission. A worker only owns an operation while the
// recorded uid matches its own. Any other state marks the operation as concluded for that worker.
//
// Ownership and concurrency
// ---------------------------------------------------------------------------------------------------------------------
// Only one operation is active per node. Submission rejects a new operation while an active one exists, unless the
// existing operation is stale. A stale operation has a worker that has not touched its heartbeat within the stale
// window. The sweep uses the same rule to adopt operations that no live worker owns.
//
// Every control plane node runs an instance of the coordinator. Before a worker adopts a stale operation, the sweep
// claims it with a compare-and-swap write that stamps the heartbeat. A conflicting claim means another instance
// adopted the operation first, and the loser skips it. A worker that later loses a race on a write also stops
// quietly, because a conflict means another instance owns the operation now.
//
// Recovery rules
// ---------------------------------------------------------------------------------------------------------------------
// A failed upgrade that reached the node marks the operation with RecoveryRequired. Until the upgrade is retried or
// the node is uncordoned, no other operation may start on that node. This makes sure that a node is never left
// cordoned with an unknown binary version while other maintenance proceeds.

package maintenance

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

// Operation model
// =====================================================================================================================

const (
	KindCordonDrain = "cordon-drain"
	KindCordon      = "cordon"
	KindUncordon    = "uncordon"
	KindUpgrade     = "upgrade"
	KindOsUpdate    = "os-update"
	KindRemove      = "remove"

	PhasePending   = "Pending"
	PhaseRunning   = "Running"
	PhaseCompleted = "Completed"
	PhaseBlocked   = "Blocked"
	PhaseFailed    = "Failed"
	PhaseCancelled = "Cancelled"

	timeoutMinSeconds = 30
	timeoutMaxSeconds = 3600

	defaultTimeoutSeconds = 600

	staleAfter = 2 * time.Minute

	uncordonTimeout = 2 * time.Minute

	upgradeSubmitMargin = 25 * time.Minute
	osSubmitMargin      = 90 * time.Minute
)

var maintenanceWake = make(chan struct{}, 1)
var autoMaintenanceWake = make(chan struct{}, 1)

func maintenanceWakeSignal() {
	select {
	case maintenanceWake <- struct{}{}:
	default:
	}
}

func autoMaintenanceWakeSignal() {
	select {
	case autoMaintenanceWake <- struct{}{}:
	default:
	}
}

func KindIsUpgrade(kind string) bool {
	return kind == KindUpgrade
}

func KindIsOsUpdate(kind string) bool {
	return kind == KindOsUpdate
}

func KindRequiresRecovery(kind string) bool {
	return KindIsUpgrade(kind) || KindIsOsUpdate(kind)
}

func UpgradeCordons(operation Operation) bool {
	return operation.Options.Cordon || operation.Options.Drain
}

func PhaseActive(phase string) bool {
	return phase == PhasePending || phase == PhaseRunning
}

type Options struct {
	TimeoutSeconds          int
	Cordon                  bool
	Drain                   bool
	DeleteVolatilePods      bool
	BypassDisruptionBudgets bool
	ForceDelete             bool
}

type Operation struct {
	Progress                   shared.NodeAgentProgress `json:"progress,omitempty"`
	ExecutorSeenAt             time.Time                `json:"executorSeenAt,omitempty"`
	Uid                        string                   `json:"uid"`
	NodeName                   string                   `json:"nodeName"`
	NodeUid                    string                   `json:"nodeUid"`
	Kind                       string                   `json:"kind"`
	Phase                      string                   `json:"phase"`
	Options                    Options                  `json:"options"`
	StartedAt                  time.Time                `json:"startedAt"`
	UpdatedAt                  time.Time                `json:"updatedAt"`
	HeartbeatAt                time.Time                `json:"heartbeatAt"`
	Deadline                   time.Time                `json:"deadline"`
	Error                      string                   `json:"error,omitempty"`
	CancelRequested            bool                     `json:"cancelRequested"`
	OriginalUnschedulable      bool                     `json:"originalUnschedulable"`
	OriginalSchedulingCaptured bool                     `json:"originalSchedulingCaptured"`
	HostTerminationUnverified  bool                     `json:"hostTerminationUnverified"`
	TargetRelease              string                   `json:"targetRelease,omitempty"`
	ExecutorSubmitted          bool                     `json:"executorSubmitted"`
	OsPackagesStaged           bool                     `json:"osPackagesStaged,omitempty"`
	SubmittedAt                time.Time                `json:"submittedAt,omitempty"`
	AutoRunUid                 string                   `json:"autoRunUid,omitempty"`
	RecoveryRequired           bool                     `json:"recoveryRequired"`
	SuspendedJobIds            []string                 `json:"suspendedJobIds,omitempty"`
	LogStage                   string                   `json:"logStage,omitempty"`
	LogStageUpdatedAt          time.Time                `json:"logStageUpdatedAt"`
	NodeDeleted                bool                     `json:"nodeDeleted"`
	ResourcesRemoved           bool                     `json:"resourcesRemoved"`
}

func operationStale(operation Operation, now time.Time) bool {
	return now.Sub(operation.HeartbeatAt) > staleAfter
}

func operationBlockedByRecovery(operation Operation) bool {
	return KindRequiresRecovery(operation.Kind) && operation.ExecutorSubmitted && operation.RecoveryRequired
}

func submittedCancelMessage(kind string) string {
	if KindIsOsUpdate(kind) {
		return "the operating-system update was submitted to the node and cannot be cancelled"
	}
	return "the upgrade was submitted to the node and cannot be cancelled"
}

// Public API
// =====================================================================================================================

func Start(nodeName string, nodeUid string, options Options) error {
	options.Cordon = true
	options.Drain = true
	return submitStart(nodeName, nodeUid, KindCordonDrain, options, "", "")
}

func Cordon(nodeName string, nodeUid string, options Options) error {
	options.Cordon = true
	return submitStart(nodeName, nodeUid, KindCordon, options, "", "")
}

func Uncordon(nodeName string, nodeUid string) error {
	return submitStart(
		nodeName,
		nodeUid,
		KindUncordon,
		Options{TimeoutSeconds: defaultTimeoutSeconds},
		"",
		"",
	)
}

func UpgradeStart(nodeName string, nodeUid string, release string, options Options) error {
	return submitStart(nodeName, nodeUid, KindUpgrade, options, release, "")
}

func RemoveStart(nodeName string, nodeUid string, options Options) error {
	if err := removeSubmitGuard(nodeName); err != nil {
		return err
	}
	return submitStart(nodeName, nodeUid, KindRemove, options, "", "")
}

func OsUpdateStart(nodeName string, nodeUid string, options Options, autoRunUid string) error {
	options.Cordon = true
	options.Drain = true
	return submitStart(nodeName, nodeUid, KindOsUpdate, options, "", autoRunUid)
}

func CancelActiveOperation(nodeName string) error {
	nodeName = strings.TrimSpace(nodeName)
	if nodeName == "" {
		return errors.New("no node name was provided")
	}

	client, err := stackClientNew()
	if err != nil {
		return err
	}

	record, err := stackRead(client, stackNodeKey(nodeName))
	if err != nil {
		return err
	}

	if !record.Found || record.Value.NodeName == "" {
		return fmt.Errorf("no operation exists for node %s", nodeName)
	}
	operation := record.Value
	if !PhaseActive(operation.Phase) {
		return fmt.Errorf("the operation on node %s already finished", nodeName)
	}
	if KindRequiresRecovery(operation.Kind) && operation.ExecutorSubmitted {
		return errors.New(submittedCancelMessage(operation.Kind))
	}
	if removePastPointOfNoReturn(operation) {
		return errors.New("the node removal passed the point of no return and cannot be cancelled")
	}
	if operation.CancelRequested {
		return nil
	}

	operation.CancelRequested = true
	operation.UpdatedAt = time.Now().UTC()
	operation.HeartbeatAt = time.Time{}

	value, marshalErr := json.Marshal(operation)
	if marshalErr != nil {
		return marshalErr
	}

	for attempt := 0; ; attempt++ {
		_, writeErr := stackWrite(client, stackNodeKey(nodeName), value, record.Revision)
		if writeErr == nil {
			return nil
		}
		if !shared.IsConflict(writeErr) || attempt >= 8 {
			return writeErr
		}

		time.Sleep(2 * time.Second)

		record, err = stackRead(client, stackNodeKey(nodeName))
		if err != nil {
			return err
		}
		if !record.Found || record.Value.NodeName == "" {
			return fmt.Errorf("no operation exists for node %s", nodeName)
		}
		if !PhaseActive(record.Value.Phase) {
			return fmt.Errorf("the operation on node %s already finished", nodeName)
		}
		if KindRequiresRecovery(record.Value.Kind) && record.Value.ExecutorSubmitted {
			return errors.New(submittedCancelMessage(record.Value.Kind))
		}
		if removePastPointOfNoReturn(record.Value) {
			return errors.New("the node removal passed the point of no return and cannot be cancelled")
		}

		operation = record.Value
		operation.CancelRequested = true
		operation.UpdatedAt = time.Now().UTC()
		operation.HeartbeatAt = time.Time{}

		value, marshalErr = json.Marshal(operation)
		if marshalErr != nil {
			return marshalErr
		}
	}
}

func Snapshot() (map[string]Operation, error) {
	client, err := stackClientNew()
	if err != nil {
		return nil, err
	}

	records, err := stackList(client, stackKeyPrefix)
	if err != nil {
		return nil, err
	}

	snapshot := map[string]Operation{}
	for _, record := range records {
		if record.IsEmpty() {
			continue
		}

		var operation Operation
		if err := json.Unmarshal(record.Value, &operation); err != nil {
			return nil, fmt.Errorf("could not parse the maintenance record %s: %s", record.Key, err)
		}

		if operation.NodeName == "" {
			continue
		}

		operation.SuspendedJobIds = append([]string(nil), operation.SuspendedJobIds...)
		snapshot[operation.NodeName] = operation
	}
	return snapshot, nil
}

// Submission
// =====================================================================================================================

type submission struct {
	nodeName   string
	nodeUid    string
	kind       string
	options    Options
	release    string
	autoRunUid string
}

func submitStart(
	nodeName string,
	nodeUid string,
	kind string,
	options Options,
	release string,
	autoRunUid string,
) error {
	nodeName = strings.TrimSpace(nodeName)
	if nodeName == "" {
		return errors.New("no node name was provided")
	}
	if nodeUid == "" && kind != KindRemove {
		return errors.New("no node uid was provided")
	}
	if KindIsUpgrade(kind) {
		if _, ok := shared.ReleaseByExactVersion(release); !ok {
			return fmt.Errorf("the release %s is not a known Kubernetes release", release)
		}
		if options.ForceDelete {
			return errors.New("force delete cannot be used with node upgrades because it is not possible to prove that the workloads stopped")
		}
	}
	if KindIsOsUpdate(kind) && (options.ForceDelete || options.BypassDisruptionBudgets) {
		return errors.New("the operating-system update cannot delete pods forcefully or bypass the disruption budgets")
	}
	if err := clusterReadyGuard(); err != nil {
		return err
	}

	return submitWrite(submission{
		nodeName:   nodeName,
		nodeUid:    nodeUid,
		kind:       kind,
		options:    options,
		release:    release,
		autoRunUid: autoRunUid,
	})
}

func submitValid(submit submission, existing Operation, exists bool) error {
	if err := validateOptions(submit.options); err != nil {
		return err
	}

	if !exists {
		return nil
	}

	if PhaseActive(existing.Phase) && !operationStale(existing, time.Now()) {
		return fmt.Errorf("an operation is already active for node %s", submit.nodeName)
	}

	if operationBlockedByRecovery(existing) && submit.kind != KindUncordon && submit.kind != KindRemove {
		switch {
		case KindIsUpgrade(submit.kind):
			if !KindIsUpgrade(existing.Kind) {
				return fmt.Errorf(
					"node %s must retry the failed operating-system update first",
					submit.nodeName,
				)
			}
			if existing.NodeUid != submit.nodeUid {
				return fmt.Errorf("the node uid does not match the failed upgrade on node %s", submit.nodeName)
			}
			if existing.TargetRelease != submit.release {
				return fmt.Errorf(
					"the release %s does not match the failed upgrade target %s on node %s",
					submit.release,
					existing.TargetRelease,
					submit.nodeName,
				)
			}
		case KindIsOsUpdate(submit.kind):
			if !KindIsOsUpdate(existing.Kind) {
				return fmt.Errorf(
					"node %s must retry the failed upgrade first",
					submit.nodeName,
				)
			}
			if existing.NodeUid != submit.nodeUid {
				return fmt.Errorf("the node uid does not match the failed operating-system update on node %s", submit.nodeName)
			}
		default:
			return fmt.Errorf(
				"node %s requires recovery from a failed node operation before a new operation can start. Retry the failed operation or uncordon the node",
				submit.nodeName,
			)
		}
	}

	if existing.HostTerminationUnverified && submit.kind != KindRemove && submit.kind != KindUncordon {
		return fmt.Errorf(
			"node %s has unverified host terminations from a previous force delete and cannot be maintained",
			submit.nodeName,
		)
	}

	return nil
}

func submitRecord(submit submission, existing Operation) Operation {
	now := time.Now().UTC()

	operation := Operation{
		Uid:                       shared.NewUid(),
		NodeName:                  submit.nodeName,
		NodeUid:                   submit.nodeUid,
		Kind:                      submit.kind,
		Phase:                     PhasePending,
		Options:                   submit.options,
		StartedAt:                 now,
		UpdatedAt:                 now,
		HeartbeatAt:               now,
		Deadline:                  now.Add(time.Duration(submit.options.TimeoutSeconds) * time.Second),
		HostTerminationUnverified: existing.HostTerminationUnverified,
		TargetRelease:             submit.release,
		AutoRunUid:                submit.autoRunUid,
	}

	if submit.kind == KindUncordon {
		operation.Deadline = now.Add(uncordonTimeout)
	}

	if KindIsUpgrade(submit.kind) {
		operation.Deadline = now.Add(time.Duration(submit.options.TimeoutSeconds)*time.Second + upgradeSubmitMargin)
	}

	if KindIsOsUpdate(submit.kind) {
		operation.Deadline = now.Add(time.Duration(submit.options.TimeoutSeconds)*time.Second + osSubmitMargin)
	}

	if operationBlockedByRecovery(existing) {
		operation.SuspendedJobIds = append([]string(nil), existing.SuspendedJobIds...)
		if submit.options.Cordon || submit.options.Drain {
			operation.OriginalUnschedulable = existing.OriginalUnschedulable
			operation.OriginalSchedulingCaptured = existing.OriginalSchedulingCaptured
		}
	}

	if submit.kind == KindRemove && existing.Kind == KindRemove {
		operation.NodeDeleted = existing.NodeDeleted
		operation.ResourcesRemoved = existing.ResourcesRemoved
		operation.SuspendedJobIds = append([]string(nil), existing.SuspendedJobIds...)
	}

	return operation
}

func submitWrite(submit submission) error {
	client, err := stackClientNew()
	if err != nil {
		return err
	}
	rollingCondition, rollingErr := rollingUpgradeNodeGuard(client, submit.nodeName)
	if rollingErr != nil {
		return rollingErr
	}

	record, readErr := stackRead(client, stackNodeKey(submit.nodeName))
	if readErr != nil {
		return readErr
	}
	found := record.Found

	existing := record.Value
	exists := found && existing.NodeName != ""
	if validErr := submitValid(submit, existing, exists); validErr != nil {
		return validErr
	}

	operation := submitRecord(submit, existing)

	value, marshalErr := json.Marshal(operation)
	if marshalErr != nil {
		return marshalErr
	}

	expectedRevision := int64(0)
	if found {
		expectedRevision = record.Revision
	}

	conditions := []ucxapi.StackStateRevisionCondition{rollingCondition}
	validUntil := util.OptNone[int64]()
	if KindIsOsUpdate(submit.kind) && submit.autoRunUid != "" {
		gate, gateErr := autoOsScheduleGate(client, time.Now().UTC())
		if gateErr != nil {
			return gateErr
		}
		if !gate.open {
			return errAutoOsSchedulePaused
		}
		conditions = append(conditions, gate.condition)
		validUntil = util.OptValue(gate.deadline.UnixMilli())
	}

	_, writeErr := shared.RpcGrantStateWriteChecked(
		client,
		stackNodeKey(submit.nodeName),
		value,
		expectedRevision,
		conditions,
		nil,
		validUntil,
	)
	if writeErr != nil {
		return writeErr
	}

	resetNodeLog(submit.nodeName, operation.Uid)
	maintenanceWakeSignal()

	return nil
}

// Guards
// =====================================================================================================================
// The guards run before an operation is accepted. They read the cluster record to make sure the cluster is in a
// state where maintenance is safe.

func validateOptions(options Options) error {
	if options.TimeoutSeconds < timeoutMinSeconds || options.TimeoutSeconds > timeoutMaxSeconds {
		return fmt.Errorf(
			"the timeout must be between %d and %d seconds",
			timeoutMinSeconds,
			timeoutMaxSeconds,
		)
	}

	if options.ForceDelete && !options.BypassDisruptionBudgets {
		return errors.New("force delete requires bypassing the disruption budgets")
	}

	return nil
}

func clusterReadyGuard() error {
	record, err := readClusterRecord()
	if err != nil {
		return fmt.Errorf("could not read the cluster record: %s", err)
	}
	if record.Phase != "created" {
		return fmt.Errorf("the cluster is not ready for maintenance (state: %s)", record.Phase)
	}

	return recoveryIntentGuard()
}

func recoveryIntentGuard() error {
	active, err := recoveryIntentActive()
	if err != nil {
		return err
	}
	if active {
		return errors.New("a recovery operation is in progress for this cluster")
	}
	return nil
}

func recoveryIntentActive() (bool, error) {
	client, err := shared.ClusterStateClientNewHost()
	if err != nil {
		return false, fmt.Errorf("could not read the recovery state: %s", err)
	}

	active, err := shared.ClusterRecoveryActive(client)
	if err != nil {
		return false, fmt.Errorf("could not read the recovery state: %s", err)
	}

	return active, nil
}

func removeSubmitGuard(nodeName string) error {
	record, err := readClusterRecord()
	if err != nil {
		return fmt.Errorf("could not read the cluster record: %s", err)
	}
	if record.Phase != "created" {
		return fmt.Errorf("the cluster is not ready for maintenance (state: %s)", record.Phase)
	}
	if err := recoveryIntentGuard(); err != nil {
		return err
	}

	client, clientErr := stackClientNew()
	if clientErr != nil {
		return fmt.Errorf("could not read the maintenance state: %s", clientErr)
	}

	existingRecord, readErr := stackRead(client, stackNodeKey(nodeName))
	if readErr != nil {
		return fmt.Errorf("could not read the maintenance state: %s", readErr)
	}

	removeRetry := existingRecord.Found && existingRecord.Value.NodeName != "" &&
		existingRecord.Value.Kind == KindRemove && !PhaseActive(existingRecord.Value.Phase)

	node, known := removeNodeRecord(record, nodeName)
	if !known && !removeRetry {
		return fmt.Errorf("the node %s is not part of this cluster", nodeName)
	}

	if known && node.Group == shared.GroupControlPlane {
		controlPlanes := controlPlaneNames(record)
		remaining := len(controlPlanes) - 1
		if remaining < 2 {
			return errors.New(
				"the control plane must keep at least two nodes for the etcd quorum, so this node cannot be removed",
			)
		}
	}

	snapshot, err := Snapshot()
	if err != nil {
		return fmt.Errorf("could not read the maintenance state: %s", err)
	}

	for otherName, other := range snapshot {
		if otherName == nodeName {
			continue
		}
		if other.Kind == KindRemove && PhaseActive(other.Phase) {
			return errors.New("another node is already being removed")
		}
	}

	return nil
}

func removeNodeRecord(record shared.ClusterRecord, nodeName string) (shared.ClusterNodeRecord, bool) {
	for _, node := range record.Nodes {
		if node.Hostname == nodeName {
			return node, true
		}
	}
	return shared.ClusterNodeRecord{}, false
}

func readClusterRecord() (shared.ClusterRecord, error) {
	record, found, err := shared.ClusterStateRecordRead()
	if err != nil {
		return shared.ClusterRecord{}, err
	}
	if !found {
		return shared.ClusterRecord{}, errors.New("the cluster record does not exist")
	}

	return record, nil
}

// Stack state
// =====================================================================================================================
// Operations are persisted in the stack state under a key per node. Every write carries the revision that the writer
// last read. The write fails when the revision moved, which turns the state into the single source of truth for
// operation ownership.

const (
	stackKeyPrefix = "maintenance/nodes/"

	stackListPageSize = 100
)

type stackClient = shared.RpcGrantClient

func stackClientNew() (stackClient, error) {
	return shared.RpcGrantClientNew()
}

type stackValue[T any] struct {
	Found    bool
	Value    T
	Revision int64
}

type stackRecord = stackValue[Operation]

func stackValueDecode[T any](record ucxapi.StackStateRecord, noun string) (stackValue[T], error) {
	result := stackValue[T]{
		Found:    true,
		Revision: record.Revision,
	}

	if record.IsEmpty() {
		return result, nil
	}

	if err := json.Unmarshal(record.Value, &result.Value); err != nil {
		return stackValue[T]{}, fmt.Errorf("could not parse the %s: %s", noun, err)
	}

	return result, nil
}

func stackValueRead[T any](client stackClient, key string, noun string) (stackValue[T], error) {
	record, found, err := shared.RpcGrantStateRead(client, key)
	if err != nil {
		return stackValue[T]{}, err
	}

	if !found {
		return stackValue[T]{}, nil
	}

	return stackValueDecode[T](record, noun)
}

func stackNodeKey(nodeName string) string {
	return fmt.Sprintf("%s%s", stackKeyPrefix, nodeName)
}

func stackRead(client stackClient, key string) (stackRecord, error) {
	return stackValueRead[Operation](client, key, "maintenance record "+key)
}

func stackList(client stackClient, prefix string) ([]ucxapi.StackStateRecord, error) {
	records := []ucxapi.StackStateRecord{}
	next := util.OptNone[string]()

	for {
		page, err := shared.RpcGrantStateList(client, prefix, next, stackListPageSize)
		if err != nil {
			return nil, err
		}

		records = append(records, page.Items...)
		if !page.Next.Present {
			return records, nil
		}
		next = page.Next
	}
}

func stackWrite(client stackClient, key string, value json.RawMessage, expectedRevision int64) (int64, error) {
	return shared.RpcGrantStateWrite(client, key, value, expectedRevision)
}
