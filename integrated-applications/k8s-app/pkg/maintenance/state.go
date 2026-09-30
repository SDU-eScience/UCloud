package maintenance

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

const maintenancePhaseRequested = "requested"
const maintenancePhaseValidating = "validating"
const maintenancePhaseCordoning = "cordoning"
const maintenancePhaseDraining = "draining"
const maintenancePhaseCompleted = "completed"
const maintenancePhaseBlocked = "blocked"
const maintenancePhaseFailed = "failed"
const maintenancePhaseCancelled = "cancelled"

const (
	MaintenancePhaseCompleted = maintenancePhaseCompleted
	MaintenancePhaseBlocked   = maintenancePhaseBlocked
	MaintenancePhaseFailed    = maintenancePhaseFailed
	MaintenancePhaseCancelled = maintenancePhaseCancelled
	MaintenanceKindUncordon   = maintenanceKindUncordon
	MaintenanceKindCordon     = maintenanceKindCordon
)

const maintenanceTimeoutMinSeconds = 30
const maintenanceTimeoutMaxSeconds = 3600

const maintenanceStoreSchemaRevision = 1

const maintenanceStatePath = "/etc/ucloud-k8s/management/maintenance.json"

const maintenanceStoreLockWait = 10 * time.Second
const maintenanceStoreLockRetryDelay = 100 * time.Millisecond

var maintenancePhases = []string{
	maintenancePhaseRequested,
	maintenancePhaseValidating,
	maintenancePhaseCordoning,
	maintenancePhaseDraining,
}

var maintenanceMu sync.Mutex

type MaintenanceOptions struct {
	TimeoutSeconds          int
	DeleteVolatilePods      bool
	BypassDisruptionBudgets bool
	ForceDelete             bool
}

type MaintenanceOperation struct {
	NodeName              string
	NodeUid               string
	Kind                  string
	Phase                 string
	Options               MaintenanceOptions
	OriginalUnschedulable bool
	StartedAt             time.Time
	UpdatedAt             time.Time
	Deadline              time.Time
	Error                 string
	CancelRequested       bool
}

type maintenanceRecord struct {
	SchemaRevision int                             `json:"schemaRevision"`
	Operations     map[string]MaintenanceOperation `json:"operations"`
}

func MaintenanceSnapshot() (map[string]MaintenanceOperation, error) {
	maintenanceMu.Lock()
	defer maintenanceMu.Unlock()

	data, err := os.ReadFile(maintenanceStatePath)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return map[string]MaintenanceOperation{}, nil
		}
		return nil, err
	}

	var record maintenanceRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}

	if record.SchemaRevision != maintenanceStoreSchemaRevision {
		return nil, errors.New("the maintenance store was written by an incompatible version of the application")
	}

	snapshot := make(map[string]MaintenanceOperation, len(record.Operations))
	for name, operation := range record.Operations {
		snapshot[name] = operation
	}
	return snapshot, nil
}

func MaintenanceStart(nodeName string, nodeUid string, options MaintenanceOptions) error {
	return maintenanceSubmit(nodeName, nodeUid, options, maintenanceKindCordonDrain)
}

func MaintenanceRetry(nodeName string, nodeUid string, options MaintenanceOptions) error {
	return maintenanceSubmit(nodeName, nodeUid, options, maintenanceKindCordonDrainRetry)
}

func maintenanceSubmit(nodeName string, nodeUid string, options MaintenanceOptions, kind string) error {
	nodeName = strings.TrimSpace(nodeName)
	if nodeName == "" {
		return errors.New("no node name was provided")
	}
	if nodeUid == "" {
		return errors.New("no node uid was provided")
	}
	if err := maintenanceValidateOptions(options); err != nil {
		return err
	}

	maintenanceMu.Lock()
	defer maintenanceMu.Unlock()

	release, err := maintenanceLockStore()
	if err != nil {
		return err
	}
	defer release()

	operations, err := maintenanceReadStore()
	if err != nil {
		return err
	}

	existing, exists := operations[nodeName]
	if exists && MaintenancePhaseActive(existing.Phase) {
		return fmt.Errorf("an operation is already active for node %s", nodeName)
	}

	now := time.Now().UTC()
	operations[nodeName] = MaintenanceOperation{
		NodeName:  nodeName,
		NodeUid:   nodeUid,
		Kind:      kind,
		Phase:     maintenancePhaseRequested,
		Options:   options,
		StartedAt: now,
		UpdatedAt: now,
		Deadline:  now.Add(time.Duration(options.TimeoutSeconds) * time.Second),
	}

	if err := maintenanceWriteStore(operations); err != nil {
		return err
	}

	maintenanceSignalSubmit(nodeName)
	return nil
}

func MaintenanceUncordon(nodeName string, nodeUid string) error {
	nodeName = strings.TrimSpace(nodeName)
	if nodeName == "" {
		return errors.New("no node name was provided")
	}
	if nodeUid == "" {
		return errors.New("no node uid was provided")
	}

	maintenanceMu.Lock()
	defer maintenanceMu.Unlock()

	release, err := maintenanceLockStore()
	if err != nil {
		return err
	}
	defer release()

	operations, err := maintenanceReadStore()
	if err != nil {
		return err
	}

	existing, exists := operations[nodeName]
	if exists && MaintenancePhaseActive(existing.Phase) {
		return fmt.Errorf("an operation is already active for node %s", nodeName)
	}

	now := time.Now().UTC()
	operations[nodeName] = MaintenanceOperation{
		NodeName:  nodeName,
		NodeUid:   nodeUid,
		Kind:      maintenanceKindUncordon,
		Phase:     maintenancePhaseRequested,
		StartedAt: now,
		UpdatedAt: now,
		Deadline:  now.Add(maintenanceUncordonTimeout),
	}

	if err := maintenanceWriteStore(operations); err != nil {
		return err
	}

	maintenanceSignalSubmit(nodeName)
	return nil
}

func MaintenanceCordon(nodeName string, nodeUid string, options MaintenanceOptions) error {
	return maintenanceSubmit(nodeName, nodeUid, options, maintenanceKindCordon)
}

func MaintenanceCancel(nodeName string) error {
	nodeName = strings.TrimSpace(nodeName)
	if nodeName == "" {
		return errors.New("no node name was provided")
	}

	maintenanceMu.Lock()
	defer maintenanceMu.Unlock()

	release, err := maintenanceLockStore()
	if err != nil {
		return err
	}
	defer release()

	operations, err := maintenanceReadStore()
	if err != nil {
		return err
	}

	operation, exists := operations[nodeName]
	if !exists {
		return fmt.Errorf("no operation exists for node %s", nodeName)
	}

	if !MaintenancePhaseActive(operation.Phase) {
		return fmt.Errorf("the operation on node %s already finished", nodeName)
	}

	operation.CancelRequested = true
	operations[nodeName] = operation
	if err := maintenanceWriteStore(operations); err != nil {
		return err
	}

	maintenanceSignalSubmit(nodeName)
	return nil
}

func MaintenancePhaseActive(phase string) bool {
	for _, active := range maintenancePhases {
		if phase == active {
			return true
		}
	}
	return false
}

func maintenanceValidateOptions(options MaintenanceOptions) error {
	if options.TimeoutSeconds < maintenanceTimeoutMinSeconds || options.TimeoutSeconds > maintenanceTimeoutMaxSeconds {
		return fmt.Errorf(
			"the timeout must be between %d and %d seconds",
			maintenanceTimeoutMinSeconds,
			maintenanceTimeoutMaxSeconds,
		)
	}

	if options.ForceDelete && !options.BypassDisruptionBudgets {
		return errors.New("force delete requires bypassing the disruption budgets")
	}

	return nil
}

func maintenanceReadStore() (map[string]MaintenanceOperation, error) {
	data, err := os.ReadFile(maintenanceStatePath)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return map[string]MaintenanceOperation{}, nil
		}
		return nil, err
	}

	var record maintenanceRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, err
	}

	if record.SchemaRevision != maintenanceStoreSchemaRevision {
		return nil, errors.New("the maintenance store was written by an incompatible version of the application")
	}

	operations := make(map[string]MaintenanceOperation, len(record.Operations))
	for name, operation := range record.Operations {
		operations[name] = operation
	}
	return operations, nil
}

func maintenanceWriteStore(operations map[string]MaintenanceOperation) error {
	record := maintenanceRecord{
		SchemaRevision: maintenanceStoreSchemaRevision,
		Operations:     operations,
	}

	data, err := json.MarshalIndent(record, "", "  ")
	if err != nil {
		return err
	}

	tmpPath := fmt.Sprintf("%s.tmp", maintenanceStatePath)
	file, err := os.OpenFile(tmpPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0660)
	if err != nil {
		return err
	}

	_, err = file.Write(data)
	if err == nil {
		err = file.Sync()
	}
	if closeErr := file.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		_ = os.Remove(tmpPath)
		return err
	}

	if err := os.Rename(tmpPath, maintenanceStatePath); err != nil {
		_ = os.Remove(tmpPath)
		return err
	}

	return nil
}

func maintenanceLockStore() (func(), error) {
	return maintenanceFlock(fmt.Sprintf("%s.lock", maintenanceStatePath), maintenanceStoreLockWait)
}

func maintenanceFlock(path string, wait time.Duration) (func(), error) {
	file, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0660)
	if err != nil {
		return nil, err
	}

	lockCtx, cancel := context.WithTimeout(context.Background(), wait)
	defer cancel()

	for {
		err := syscall.Flock(int(file.Fd()), syscall.LOCK_EX|syscall.LOCK_NB)
		if err == nil {
			return func() {
				_ = syscall.Flock(int(file.Fd()), syscall.LOCK_UN)
				_ = file.Close()
			}, nil
		}

		if !errors.Is(err, syscall.EWOULDBLOCK) {
			_ = file.Close()
			return nil, err
		}

		select {
		case <-lockCtx.Done():
			_ = file.Close()
			return nil, fmt.Errorf("the maintenance store is busy: %s", path)
		case <-time.After(maintenanceStoreLockRetryDelay):
		}
	}
}

func maintenanceOperationPath(nodeName string) string {
	return fmt.Sprintf("%s.%s", maintenanceStatePath, sanitizeForLock(nodeName))
}

func maintenanceMutate(nodeName string, expectedUid string, expectedStartedAt time.Time, mutate func(*MaintenanceOperation) bool) error {
	maintenanceMu.Lock()
	defer maintenanceMu.Unlock()

	release, err := maintenanceLockStore()
	if err != nil {
		log.Warn("k8s-app maintenance: could not lock the operation store: %s", err)
		return err
	}
	defer release()

	operations, err := maintenanceReadStore()
	if err != nil {
		log.Warn("k8s-app maintenance: could not reload the operation store: %s", err)
		return err
	}

	operation, exists := operations[nodeName]
	if !exists {
		return maintenanceErrConcluded
	}

	if expectedUid != "" && (operation.NodeUid != expectedUid || !operation.StartedAt.Equal(expectedStartedAt)) {
		log.Info(
			"k8s-app maintenance %s: skipping the update of a replaced operation (was %s, now %s)",
			nodeName,
			expectedUid,
			operation.NodeUid,
		)
		return maintenanceErrConcluded
	}

	operation.UpdatedAt = time.Now().UTC()
	if !mutate(&operation) {
		return nil
	}

	operations[nodeName] = operation
	if err := maintenanceWriteStore(operations); err != nil {
		log.Warn("k8s-app maintenance: could not persist the operation store: %s", err)
		return err
	}

	if MaintenancePhaseActive(operation.Phase) {
		maintenanceSignalSubmit(nodeName)
	}
	return nil
}

func sanitizeForLock(value string) string {
	var builder strings.Builder
	for _, r := range value {
		switch {
		case r >= 'a' && r <= 'z', r >= 'A' && r <= 'Z', r >= '0' && r <= '9':
			builder.WriteRune(r)
		default:
			builder.WriteByte('-')
		}
	}
	return builder.String()
}

func maintenanceReadClusterRecord() (shared.ClusterRecord, error) {
	data, err := os.ReadFile(filepath.Join(shared.ManagementMountPath, filepath.Base(shared.ClusterRecordPath)))
	if err != nil {
		return shared.ClusterRecord{}, err
	}

	var record shared.ClusterRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return shared.ClusterRecord{}, err
	}

	return record, nil
}

func maintenanceKnownHostnames(record shared.ClusterRecord) map[string]bool {
	known := make(map[string]bool, len(record.Nodes))
	for _, node := range record.Nodes {
		if node.Hostname != "" {
			known[node.Hostname] = true
		}
	}
	return known
}

func maintenanceNamespacedName(namespace string, name string) string {
	if namespace == "" {
		return name
	}
	return fmt.Sprintf("%s/%s", namespace, name)
}
