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
	"os"
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

	PhasePending   = "pending"
	PhaseRunning   = "running"
	PhaseCompleted = "completed"
	PhaseBlocked   = "blocked"
	PhaseFailed    = "failed"
	PhaseCancelled = "cancelled"

	timeoutMinSeconds = 30
	timeoutMaxSeconds = 3600

	defaultTimeoutSeconds = 600

	staleAfter = 2 * time.Minute

	uncordonTimeout = 2 * time.Minute

	upgradeSubmitMargin = 25 * time.Minute
)

func KindIsUpgrade(kind string) bool {
	return kind == KindUpgrade
}

func PhaseActive(phase string) bool {
	return phase == PhasePending || phase == PhaseRunning
}

type Options struct {
	TimeoutSeconds          int
	DeleteVolatilePods      bool
	BypassDisruptionBudgets bool
	ForceDelete             bool
}

type Operation struct {
	Uid                        string    `json:"uid"`
	NodeName                   string    `json:"nodeName"`
	NodeUid                    string    `json:"nodeUid"`
	Kind                       string    `json:"kind"`
	Phase                      string    `json:"phase"`
	Options                    Options   `json:"options"`
	StartedAt                  time.Time `json:"startedAt"`
	UpdatedAt                  time.Time `json:"updatedAt"`
	HeartbeatAt                time.Time `json:"heartbeatAt"`
	Deadline                   time.Time `json:"deadline"`
	Error                      string    `json:"error,omitempty"`
	CancelRequested            bool      `json:"cancelRequested"`
	OriginalUnschedulable      bool      `json:"originalUnschedulable"`
	OriginalSchedulingCaptured bool      `json:"originalSchedulingCaptured"`
	HostTerminationUnverified  bool      `json:"hostTerminationUnverified"`
	TargetRelease              string    `json:"targetRelease,omitempty"`
	ExecutorSubmitted          bool      `json:"executorSubmitted"`
	RecoveryRequired           bool      `json:"recoveryRequired"`
	SuspendedJobIds            []string  `json:"suspendedJobIds,omitempty"`
	LogStage                   string    `json:"logStage,omitempty"`
	LogStageUpdatedAt          time.Time `json:"logStageUpdatedAt"`
}

func operationStale(operation Operation, now time.Time) bool {
	return now.Sub(operation.HeartbeatAt) > staleAfter
}

func upgradeBlockedByRecovery(operation Operation) bool {
	return KindIsUpgrade(operation.Kind) && operation.ExecutorSubmitted && operation.RecoveryRequired
}

// Public API
// =====================================================================================================================

func Start(nodeName string, nodeUid string, options Options) error {
	return submitStart(nodeName, nodeUid, KindCordonDrain, options, "")
}

func Cordon(nodeName string, nodeUid string, options Options) error {
	return submitStart(nodeName, nodeUid, KindCordon, options, "")
}

func Uncordon(nodeName string, nodeUid string) error {
	return submitStart(
		nodeName,
		nodeUid,
		KindUncordon,
		Options{TimeoutSeconds: defaultTimeoutSeconds},
		"",
	)
}

func UpgradeStart(nodeName string, nodeUid string, release string, options Options) error {
	return submitStart(nodeName, nodeUid, KindUpgrade, options, release)
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

	if !record.Found || record.Operation.NodeName == "" {
		return fmt.Errorf("no operation exists for node %s", nodeName)
	}
	operation := record.Operation
	if !PhaseActive(operation.Phase) {
		return fmt.Errorf("the operation on node %s already finished", nodeName)
	}
	if KindIsUpgrade(operation.Kind) && operation.ExecutorSubmitted {
		return errors.New("the upgrade was submitted to the node and cannot be cancelled")
	}
	if operation.CancelRequested {
		return nil
	}

	operation.CancelRequested = true
	operation.UpdatedAt = time.Now().UTC()

	value, marshalErr := json.Marshal(operation)
	if marshalErr != nil {
		return marshalErr
	}

	_, writeErr := stackWrite(client, stackNodeKey(nodeName), value, record.Revision)
	if writeErr != nil {
		return writeErr
	}

	return nil
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

func newOperationUid() string {
	holder := ""
	if hostname, err := os.Hostname(); err == nil {
		holder = strings.TrimSpace(hostname)
	}
	return fmt.Sprintf("%s-%d-%s", shared.SanitizeForPath(holder), time.Now().UnixNano(), util.SecureToken())
}

type submission struct {
	nodeName string
	nodeUid  string
	kind     string
	options  Options
	release  string
}

func submitStart(
	nodeName string,
	nodeUid string,
	kind string,
	options Options,
	release string,
) error {
	nodeName = strings.TrimSpace(nodeName)
	if nodeName == "" {
		return errors.New("no node name was provided")
	}
	if nodeUid == "" {
		return errors.New("no node uid was provided")
	}
	if KindIsUpgrade(kind) {
		if _, ok := shared.ReleaseByExactVersion(release); !ok {
			return fmt.Errorf("the release %s is not a known k3s release", release)
		}
		if options.ForceDelete {
			return errors.New("force delete cannot be used with node upgrades because it is not possible to prove that the workloads stopped")
		}
	}
	if err := clusterReadyGuard(); err != nil {
		return err
	}

	return submitWrite(submission{
		nodeName: nodeName,
		nodeUid:  nodeUid,
		kind:     kind,
		options:  options,
		release:  release,
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

	if upgradeBlockedByRecovery(existing) && submit.kind != KindUncordon {
		if !KindIsUpgrade(submit.kind) {
			return fmt.Errorf(
				"node %s requires upgrade recovery before a new operation can start; retry the upgrade or uncordon the node",
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
	}

	if existing.HostTerminationUnverified {
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
		Uid:                       newOperationUid(),
		NodeName:                  submit.nodeName,
		NodeUid:                   submit.nodeUid,
		Kind:                      submit.kind,
		Phase:                     PhasePending,
		Options:                   submit.options,
		StartedAt:                 now,
		UpdatedAt:                 now,
		Deadline:                  now.Add(time.Duration(submit.options.TimeoutSeconds) * time.Second),
		HostTerminationUnverified: existing.HostTerminationUnverified,
		TargetRelease:             submit.release,
	}

	if submit.kind == KindUncordon {
		operation.Deadline = now.Add(uncordonTimeout)
	}

	if KindIsUpgrade(submit.kind) {
		operation.Deadline = now.Add(time.Duration(submit.options.TimeoutSeconds)*time.Second + upgradeSubmitMargin)
	}

	if upgradeBlockedByRecovery(existing) {
		operation.OriginalUnschedulable = existing.OriginalUnschedulable
		operation.OriginalSchedulingCaptured = existing.OriginalSchedulingCaptured
		operation.SuspendedJobIds = append([]string(nil), existing.SuspendedJobIds...)
	}

	return operation
}

func submitWrite(submit submission) error {
	client, err := stackClientNew()
	if err != nil {
		return err
	}

	record, readErr := stackRead(client, stackNodeKey(submit.nodeName))
	if readErr != nil {
		return readErr
	}
	found := record.Found

	existing := record.Operation
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

	_, writeErr := stackWrite(client, stackNodeKey(submit.nodeName), value, expectedRevision)
	if writeErr != nil {
		return writeErr
	}

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
	return nil
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

type stackRecord struct {
	Found     bool
	Operation Operation
	Revision  int64
}

func stackNodeKey(nodeName string) string {
	return fmt.Sprintf("%s%s", stackKeyPrefix, nodeName)
}

func stackRead(client stackClient, key string) (stackRecord, error) {
	record, found, err := shared.RpcGrantStateRead(client, key)
	if err != nil {
		return stackRecord{}, err
	}

	if !found {
		return stackRecord{}, nil
	}

	result := stackRecord{
		Found:    true,
		Revision: record.Revision,
	}

	if record.IsEmpty() {
		return result, nil
	}

	var operation Operation
	if err := json.Unmarshal(record.Value, &operation); err != nil {
		return stackRecord{}, fmt.Errorf("could not parse the maintenance record %s: %s", key, err)
	}

	result.Operation = operation
	return result, nil
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
