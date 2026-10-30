package maintenance

import (
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strings"
	"sync"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

var runSubmitMu sync.Mutex

var errRunNotFound = errors.New("no matching maintenance run was found")

var errRunAdmissionBusy = errors.New("another maintenance run is being submitted")

func runAdmissionAcquire(client stackClient) (string, error) {
	now := time.Now().UTC()
	for attempt := 0; attempt < 5; attempt++ {
		record, err := stackValueRead[runAdmission](client, runAdmissionKey, "maintenance run admission")
		if err != nil {
			return "", err
		}
		if record.Found && record.Value.Uid != "" && now.Before(record.Value.Deadline) {
			return "", errRunAdmissionBusy
		}

		admission := runAdmission{
			SchemaRevision: 1,
			Uid:            shared.NewUid(),
			Deadline:       now.Add(runAdmissionLease),
		}
		value, marshalErr := json.Marshal(admission)
		if marshalErr != nil {
			return "", marshalErr
		}
		_, writeErr := shared.RpcGrantStateWrite(client, runAdmissionKey, value, record.Revision)
		if writeErr == nil {
			return admission.Uid, nil
		}
		if !shared.IsConflict(writeErr) {
			return "", writeErr
		}
	}
	return "", errors.New("the maintenance admission state changed. Try again")
}

func runAdmissionRelease(client stackClient, uid string) {
	for attempt := 0; attempt < 3; attempt++ {
		record, err := stackValueRead[runAdmission](client, runAdmissionKey, "maintenance run admission")
		if err != nil || !record.Found || record.Value.Uid != uid {
			return
		}
		_, writeErr := shared.RpcGrantStateWrite(client, runAdmissionKey, json.RawMessage("null"), record.Revision)
		if writeErr == nil || !shared.IsConflict(writeErr) {
			return
		}
	}
}

const (
	RunKindUpdate     = "update"
	RunKindNodeAction = "node-action"

	RunPending   = "Pending"
	RunPreflight = "Preflight"
	RunBackup    = "Backup"
	RunRunning   = "Running"
	RunBlocked   = "Blocked"
	RunCompleted = "Completed"
	RunFailed    = "Failed"
	RunCancelled = "Cancelled"

	TaskK8sUpgrade = "k8s-upgrade"
	TaskOsUpdate   = "os-update"
	TaskCordon     = "cordon"
	TaskUncordon   = "uncordon"
	TaskRemove     = "remove"

	TriggerWindow = "window"
	TriggerManual = "manual"

	runUpdateKey    = "maintenance/run/update"
	runNodePrefix   = "maintenance/runs/node/"
	runLastKey      = "maintenance/run/last"
	runHistoryKey   = "maintenance/run/history"
	runAdmissionKey = "maintenance/runs/admission"

	runAdmissionLease = 2 * time.Minute
	runHistoryLimit   = 20
)

type runAdmission struct {
	SchemaRevision int       `json:"schemaRevision"`
	Uid            string    `json:"uid"`
	Deadline       time.Time `json:"deadline"`
}

type RunHistory struct {
	SchemaRevision int              `json:"schemaRevision"`
	Runs           []MaintenanceRun `json:"runs"`
}

type Target struct {
	All   bool
	Pools []string
	Nodes []string
}

type TaskRequest struct {
	Kind                    string
	TargetRelease           string
	Preparation             string
	TimeoutSeconds          int
	DeleteVolatilePods      bool
	BypassDisruptionBudgets bool
	ForceDelete             bool
}

type RunRequest struct {
	Tasks               []TaskRequest
	Target              Target
	MaxParallelPerGroup int
	RequestedBy         string
}

type TaskSpec struct {
	Kind          string
	TargetRelease string
	Options       Options
}

type RunSpec struct {
	Kind                string
	Tasks               []TaskSpec
	Target              Target
	MaxParallelPerGroup int
	MaxBackupAge        time.Duration
}

type RunCheck struct {
	Name   string `json:"name"`
	Ok     bool   `json:"ok"`
	Detail string `json:"detail,omitempty"`
}

type RunItem struct {
	NodeName     string    `json:"nodeName"`
	NodeUid      string    `json:"nodeUid"`
	Group        string    `json:"group"`
	Phase        string    `json:"phase"`
	OperationUid string    `json:"operationUid,omitempty"`
	Error        string    `json:"error,omitempty"`
	StartedAt    time.Time `json:"startedAt,omitempty"`
}

type RunTask struct {
	Kind          string    `json:"kind"`
	TargetRelease string    `json:"targetRelease,omitempty"`
	Phase         string    `json:"phase"`
	Options       Options   `json:"options"`
	Items         []RunItem `json:"items"`
}

type MaintenanceRun struct {
	SchemaRevision   int        `json:"schemaRevision"`
	Uid              string     `json:"uid"`
	Kind             string     `json:"kind"`
	Trigger          string     `json:"trigger"`
	RequestedBy      string     `json:"requestedBy,omitempty"`
	RequestedAt      time.Time  `json:"requestedAt"`
	Request          RunRequest `json:"request"`
	Effective        RunSpec    `json:"effective"`
	WindowStart      time.Time  `json:"windowStart,omitempty"`
	WindowEnd        time.Time  `json:"windowEnd,omitempty"`
	Tasks            []RunTask  `json:"tasks,omitempty"`
	Checks           []RunCheck `json:"checks,omitempty"`
	Phase            string     `json:"phase"`
	Message          string     `json:"message,omitempty"`
	Error            string     `json:"error,omitempty"`
	CancelRequested  bool       `json:"cancelRequested,omitempty"`
	BackupRequestUid string     `json:"backupRequestUid,omitempty"`
	BackupConfirmed  bool       `json:"backupConfirmed,omitempty"`
	StartedAt        time.Time  `json:"startedAt,omitempty"`
	FinishedAt       time.Time  `json:"finishedAt,omitempty"`
	ClaimUid         string     `json:"claimUid,omitempty"`
	ClaimDeadline    time.Time  `json:"claimDeadline,omitempty"`

	osProbes map[string]shared.NodeAgentOsStatus
}

type RunStatus struct {
	PolicyFound bool
	Policy      Policy
	Schedule    ScheduleState
	ActiveRuns  []MaintenanceRun
	RecentRuns  []MaintenanceRun
	LastRun     *MaintenanceRun
}

func RunActive(run MaintenanceRun) bool {
	switch run.Phase {
	case RunPending, RunPreflight, RunBackup, RunRunning, RunBlocked, RunFailed:
		return true
	}
	return false
}

func RunTerminal(run MaintenanceRun) bool {
	return run.Phase == RunCompleted || run.Phase == RunCancelled
}

func SubmitRun(request RunRequest, requestedBy string) (MaintenanceRun, error) {
	client, err := stackClientNew()
	if err != nil {
		return MaintenanceRun{}, err
	}

	record, found, err := readClusterRecord()
	if err != nil {
		return MaintenanceRun{}, fmt.Errorf("could not read the cluster record: %s", err)
	}
	if !found {
		return MaintenanceRun{}, errors.New("the cluster record does not exist")
	}
	if !shared.ClusterRecordPhaseOperational(record.Phase) {
		return MaintenanceRun{}, fmt.Errorf("the cluster is not ready for maintenance (state: %s)", record.Phase)
	}

	policyRecord, err := PolicyRead(client)
	if err != nil {
		return MaintenanceRun{}, err
	}
	policy := DefaultPolicy()
	if policyRecord.Found {
		policy = policyRecord.Value
	}

	spec, err := buildRunSpec(policy, request)
	if err != nil {
		return MaintenanceRun{}, err
	}

	runSubmitMu.Lock()
	defer runSubmitMu.Unlock()

	admissionUid, err := runAdmissionAcquire(client)
	if err != nil {
		return MaintenanceRun{}, err
	}
	defer runAdmissionRelease(client, admissionUid)

	strictTarget := true
	if spec.Kind == RunKindNodeAction && len(spec.Tasks) > 0 && spec.Tasks[0].Kind == TaskRemove {
		strictTarget = false
	}
	nodes, err := targetNodes(record, spec, strictTarget)
	if err != nil {
		return MaintenanceRun{}, err
	}
	if spec.Kind == RunKindNodeAction && spec.Tasks[0].Kind == TaskRemove {
		if err := removeSubmitGuard(nodes[0].Hostname); err != nil {
			return MaintenanceRun{}, err
		}
	}
	if err := ensureRunCanStart(client, spec, nodes); err != nil {
		return MaintenanceRun{}, err
	}

	now := time.Now().UTC()
	run := MaintenanceRun{
		SchemaRevision: 1,
		Uid:            shared.NewUid(),
		Kind:           spec.Kind,
		Trigger:        TriggerManual,
		RequestedBy:    requestedBy,
		RequestedAt:    now,
		Request:        request,
		Effective:      spec,
		Phase:          RunPending,
	}

	if err := runWriteNew(client, run); err != nil {
		if shared.IsConflict(err) {
			return MaintenanceRun{}, errors.New("another maintenance run started while this one was submitted")
		}
		return MaintenanceRun{}, err
	}
	maintenanceWakeSignal()
	return run, nil
}

func SubmitConfiguredRun(requestedBy string) (MaintenanceRun, error) {
	client, err := stackClientNew()
	if err != nil {
		return MaintenanceRun{}, err
	}

	policyRecord, err := PolicyRead(client)
	if err != nil {
		return MaintenanceRun{}, err
	}
	policy := DefaultPolicy()
	if policyRecord.Found {
		policy = policyRecord.Value
	}

	tasks := []TaskRequest{}
	if policy.Kubernetes.Enabled {
		tasks = append(tasks, TaskRequest{Kind: TaskK8sUpgrade})
	}
	if policy.Os.Enabled {
		tasks = append(tasks, TaskRequest{Kind: TaskOsUpdate})
	}
	if len(tasks) == 0 {
		return MaintenanceRun{}, errors.New("no maintenance task is enabled in the policy")
	}

	return SubmitRun(RunRequest{
		Tasks:               tasks,
		Target:              Target{All: true},
		MaxParallelPerGroup: 1,
	}, requestedBy)
}

func createScheduledRun(client stackClient, policy Policy, windowStart time.Time, windowEnd time.Time) (MaintenanceRun, error) {
	var tasks []TaskRequest
	if policy.Kubernetes.Enabled {
		tasks = append(tasks, TaskRequest{Kind: TaskK8sUpgrade})
	}
	if policy.Os.Enabled {
		tasks = append(tasks, TaskRequest{Kind: TaskOsUpdate})
	}
	if len(tasks) == 0 {
		return MaintenanceRun{}, errors.New("no maintenance task is enabled")
	}

	request := RunRequest{
		Tasks:  tasks,
		Target: Target{All: true},
	}
	spec, err := buildRunSpec(policy, request)
	if err != nil {
		return MaintenanceRun{}, err
	}

	now := time.Now().UTC()
	run := MaintenanceRun{
		SchemaRevision: 1,
		Uid:            shared.NewUid(),
		Kind:           RunKindUpdate,
		Trigger:        TriggerWindow,
		RequestedBy:    "scheduled maintenance",
		RequestedAt:    now,
		Request:        request,
		Effective:      spec,
		WindowStart:    windowStart.UTC(),
		WindowEnd:      windowEnd.UTC(),
		Phase:          RunPending,
	}

	if err := runWriteNew(client, run); err != nil {
		return MaintenanceRun{}, err
	}
	maintenanceWakeSignal()
	return run, nil
}

func buildRunSpec(policy Policy, request RunRequest) (RunSpec, error) {
	if len(request.Tasks) == 0 {
		return RunSpec{}, errors.New("select at least one maintenance task")
	}
	if request.Target.All && (len(request.Target.Pools) > 0 || len(request.Target.Nodes) > 0) {
		return RunSpec{}, errors.New("use either all nodes, pools or nodes, not a combination")
	}

	kind := ""
	var tasks []TaskSpec
	k8sSeen := false
	osSeen := false
	nodeActionSeen := false

	for _, task := range request.Tasks {
		task.Kind = strings.ToLower(strings.TrimSpace(task.Kind))
		task.Preparation = strings.ToLower(strings.TrimSpace(task.Preparation))

		switch task.Kind {
		case TaskK8sUpgrade:
			if k8sSeen {
				return RunSpec{}, errors.New("a run can contain only one Kubernetes upgrade task")
			}
			k8sSeen = true
			kind = RunKindUpdate
			if task.Preparation != "" && task.Preparation != PreparationNone {
				return RunSpec{}, errors.New("Kubernetes upgrades cannot cordon or drain the node")
			}
			options, err := buildOptions(policy, task, PreparationNone, false)
			if err != nil {
				return RunSpec{}, err
			}
			tasks = append(tasks, TaskSpec{
				Kind:          TaskK8sUpgrade,
				TargetRelease: strings.TrimSpace(task.TargetRelease),
				Options:       options,
			})

		case TaskOsUpdate:
			if osSeen {
				return RunSpec{}, errors.New("a run can contain only one operating-system update task")
			}
			osSeen = true
			kind = RunKindUpdate
			options, err := buildOptions(policy, task, PreparationDrain, true)
			if err != nil {
				return RunSpec{}, err
			}
			tasks = append(tasks, TaskSpec{
				Kind:    TaskOsUpdate,
				Options: options,
			})

		case TaskCordon, TaskUncordon, TaskRemove:
			if nodeActionSeen {
				return RunSpec{}, errors.New("a node action run can contain only one task")
			}
			nodeActionSeen = true
			if kind != "" {
				return RunSpec{}, errors.New("node actions cannot be combined with update tasks")
			}
			kind = RunKindNodeAction

			preparation := task.Preparation
			if preparation == "" {
				preparation = PreparationNone
			}
			if task.Kind == TaskCordon && preparation == PreparationNone {
				preparation = PreparationCordon
			}
			if !PreparationValid(preparation) {
				return RunSpec{}, errors.New("the preparation must be none, cordon or drain")
			}
			options, err := buildOptions(policy, task, preparation, false)
			if err != nil {
				return RunSpec{}, err
			}
			tasks = append(tasks, TaskSpec{
				Kind:    task.Kind,
				Options: options,
			})

		default:
			return RunSpec{}, fmt.Errorf("unknown maintenance task %q", task.Kind)
		}
	}

	if kind == RunKindNodeAction {
		if len(tasks) != 1 {
			return RunSpec{}, errors.New("a node action run can contain only one task")
		}
		if request.Target.All || len(request.Target.Pools) > 0 {
			return RunSpec{}, errors.New("a node action must target one or more nodes")
		}
		if tasks[0].Kind == TaskRemove {
			if len(request.Target.Nodes) == 0 {
				return RunSpec{}, errors.New("select at least one node to remove")
			}
		} else if len(request.Target.Nodes) != 1 {
			return RunSpec{}, errors.New("a node action must target exactly one node")
		}
	}

	if kind == RunKindUpdate && nodeActionSeen {
		return RunSpec{}, errors.New("node actions cannot be combined with update tasks")
	}

	maxParallel := request.MaxParallelPerGroup
	if maxParallel <= 0 {
		maxParallel = 1
	}
	if maxParallel > 8 {
		return RunSpec{}, errors.New("the maximum parallelism per group is 8")
	}

	sort.SliceStable(tasks, func(i int, j int) bool {
		return maintenanceTaskRank(tasks[i].Kind) < maintenanceTaskRank(tasks[j].Kind)
	})

	return RunSpec{
		Kind:                kind,
		Tasks:               tasks,
		Target:              request.Target,
		MaxParallelPerGroup: maxParallel,
		MaxBackupAge:        policy.Kubernetes.MaxBackupAge,
	}, nil
}

func maintenanceTaskRank(kind string) int {
	switch kind {
	case TaskK8sUpgrade:
		return 0
	case TaskOsUpdate:
		return 1
	default:
		return 2
	}
}

func buildOptions(policy Policy, task TaskRequest, preparation string, os bool) (Options, error) {
	timeout := task.TimeoutSeconds
	if timeout == 0 {
		if os {
			timeout = policy.Os.TimeoutSeconds
		} else {
			timeout = policy.Kubernetes.TimeoutSeconds
		}
	}
	if timeout == 0 {
		timeout = defaultTimeoutSeconds
	}
	options := Options{
		TimeoutSeconds:          timeout,
		Cordon:                  preparation != PreparationNone,
		Drain:                   preparation == PreparationDrain,
		DeleteVolatilePods:      task.DeleteVolatilePods,
		BypassDisruptionBudgets: task.BypassDisruptionBudgets,
		ForceDelete:             task.ForceDelete,
	}
	if os {
		options.Cordon = true
		options.Drain = true
	}
	if err := validateOptions(options); err != nil {
		return Options{}, err
	}
	if task.Kind == TaskK8sUpgrade && options.ForceDelete {
		return Options{}, errors.New("force delete cannot be used with node upgrades")
	}
	if task.Kind == TaskOsUpdate && (options.ForceDelete || options.BypassDisruptionBudgets) {
		return Options{}, errors.New("the operating-system update cannot delete pods forcefully or bypass the disruption budgets")
	}
	if task.Kind == TaskUncordon && preparation != PreparationNone {
		return Options{}, errors.New("uncordon does not accept a preparation")
	}
	return options, nil
}

func targetNodes(record shared.ClusterRecord, spec RunSpec, strict bool) ([]shared.ClusterNodeRecord, error) {
	target := spec.Target
	if target.All {
		return append([]shared.ClusterNodeRecord(nil), record.Nodes...), nil
	}

	selected := map[string]shared.ClusterNodeRecord{}
	if len(target.Pools) > 0 {
		poolSet := map[string]bool{}
		for _, pool := range target.Pools {
			pool = strings.TrimSpace(pool)
			if pool == "" {
				continue
			}
			poolSet[pool] = true
		}
		if len(poolSet) == 0 {
			return nil, errors.New("select at least one node pool")
		}
		foundPool := map[string]bool{}
		for _, node := range record.Nodes {
			if poolSet[node.Group] {
				selected[node.Hostname] = node
				foundPool[node.Group] = true
			}
		}
		for pool := range poolSet {
			if !foundPool[pool] {
				return nil, fmt.Errorf("the node pool %s does not exist", pool)
			}
		}
	}

	if len(target.Nodes) > 0 {
		nodeSet := map[string]bool{}
		for _, name := range target.Nodes {
			name = strings.TrimSpace(name)
			if name != "" {
				nodeSet[name] = true
			}
		}
		if len(nodeSet) == 0 {
			return nil, errors.New("select at least one node")
		}
		for _, node := range record.Nodes {
			if nodeSet[node.Hostname] {
				selected[node.Hostname] = node
				delete(nodeSet, node.Hostname)
			}
		}
		for name := range nodeSet {
			if strict {
				return nil, fmt.Errorf("the node %s is not part of this cluster", name)
			}
			selected[name] = shared.ClusterNodeRecord{Hostname: name}
		}
	}

	if len(selected) == 0 {
		return nil, errors.New("the maintenance target does not contain any nodes")
	}

	nodes := make([]shared.ClusterNodeRecord, 0, len(selected))
	for _, node := range selected {
		nodes = append(nodes, node)
	}
	sort.Slice(nodes, func(i int, j int) bool {
		left := nodes[i]
		right := nodes[j]
		if left.Group != right.Group {
			if left.Group == shared.GroupControlPlane {
				return true
			}
			if right.Group == shared.GroupControlPlane {
				return false
			}
			return left.Group < right.Group
		}
		return left.Hostname < right.Hostname
	})
	return nodes, nil
}

func ensureRunCanStart(client stackClient, spec RunSpec, nodes []shared.ClusterNodeRecord) error {
	updateRecord, err := runUpdateRead(client)
	if err != nil {
		return err
	}
	nodeRuns, err := runNodeList(client)
	if err != nil {
		return err
	}

	if spec.Kind == RunKindUpdate {
		if updateRecord.Found {
			return errors.New("an update run is already active. Cancel it or wait for it to finish")
		}
		targetSet := map[string]bool{}
		for _, node := range nodes {
			targetSet[node.Hostname] = true
		}
		for _, run := range nodeRuns {
			if run.Kind == RunKindNodeAction {
				for _, nodeName := range run.Effective.Target.Nodes {
					if targetSet[nodeName] {
						return fmt.Errorf(
							"a maintenance run already exists for node %s. Retry or cancel it first",
							nodeName,
						)
					}
				}
			}
			for _, task := range run.Tasks {
				for _, item := range task.Items {
					if targetSet[item.NodeName] {
						return fmt.Errorf(
							"a maintenance run already exists for node %s. Retry or cancel it first",
							item.NodeName,
						)
					}
				}
			}
		}
		return nil
	}

	taskKind := spec.Tasks[0].Kind
	if taskKind == TaskRemove {
		for _, run := range nodeRuns {
			if run.Kind == RunKindNodeAction &&
				len(run.Effective.Tasks) > 0 &&
				run.Effective.Tasks[0].Kind == TaskRemove {
				return errors.New("another maintenance run is already removing nodes")
			}
		}
	}
	for _, node := range nodes {
		if updateRecord.Found &&
			taskKind != TaskUncordon &&
			runTargetsNode(updateRecord.Value, node.Hostname, node.Group) {
			return fmt.Errorf(
				"node %s is part of the active update run. Only uncordon is allowed on that node",
				node.Hostname,
			)
		}
	}

	for _, run := range nodeRuns {
		if run.Kind == RunKindNodeAction {
			for _, target := range run.Effective.Target.Nodes {
				for _, node := range nodes {
					if target == node.Hostname {
						return fmt.Errorf(
							"a maintenance run already exists for node %s. Retry or cancel it first",
							node.Hostname,
						)
					}
				}
			}
		}
		for _, task := range run.Tasks {
			for _, item := range task.Items {
				for _, node := range nodes {
					if item.NodeName == node.Hostname {
						return fmt.Errorf(
							"a maintenance run already exists for node %s. Retry or cancel it first",
							node.Hostname,
						)
					}
				}
			}
		}
	}

	snapshot, err := Snapshot()
	if err != nil {
		return fmt.Errorf("could not read the maintenance state: %s", err)
	}
	for _, node := range nodes {
		if operation, found := snapshot[node.Hostname]; found && PhaseActive(operation.Phase) {
			return fmt.Errorf("an operation is already active on node %s", node.Hostname)
		}
	}

	return nil
}

func runTargetsNode(run MaintenanceRun, nodeName string, nodeGroup string) bool {
	if run.Effective.Target.All {
		return true
	}
	for _, name := range run.Effective.Target.Nodes {
		if name == nodeName {
			return true
		}
	}
	for _, pool := range run.Effective.Target.Pools {
		if pool == nodeGroup {
			return true
		}
	}
	return runContainsNode(run, nodeName)
}

func runUpdateRead(client stackClient) (stackValue[MaintenanceRun], error) {
	return stackValueRead[MaintenanceRun](client, runUpdateKey, "active maintenance run")
}

func runNodeKey(nodeName string) string {
	return runNodePrefix + nodeName
}

func runNodeRecords(client stackClient) ([]stackValue[MaintenanceRun], error) {
	records, err := stackList(client, runNodePrefix)
	if err != nil {
		return nil, err
	}

	runs := make([]stackValue[MaintenanceRun], 0, len(records))
	for _, record := range records {
		if record.IsEmpty() {
			continue
		}
		var run MaintenanceRun
		if err := json.Unmarshal(record.Value, &run); err != nil {
			return nil, fmt.Errorf("could not parse the maintenance run %s: %s", record.Key, err)
		}
		if run.Uid != "" {
			runs = append(runs, stackValue[MaintenanceRun]{Found: true, Value: run, Revision: record.Revision})
		}
	}
	sort.Slice(runs, func(i int, j int) bool {
		return runs[i].Value.RequestedAt.Before(runs[j].Value.RequestedAt)
	})
	return runs, nil
}

func runNodeList(client stackClient) ([]MaintenanceRun, error) {
	records, err := runNodeRecords(client)
	if err != nil {
		return nil, err
	}
	runs := make([]MaintenanceRun, 0, len(records))
	for _, record := range records {
		runs = append(runs, record.Value)
	}
	return runs, nil
}

func runLastRead(client stackClient) (stackValue[MaintenanceRun], error) {
	return stackValueRead[MaintenanceRun](client, runLastKey, "last maintenance run")
}

func runHistoryRead(client stackClient) (stackValue[RunHistory], error) {
	return stackValueRead[RunHistory](client, runHistoryKey, "maintenance run history")
}

func runHistoryAdd(client stackClient, run MaintenanceRun) error {
	for attempt := 0; attempt < 3; attempt++ {
		record, err := runHistoryRead(client)
		if err != nil {
			return err
		}

		history := RunHistory{SchemaRevision: 1}
		if record.Found {
			history.Runs = record.Value.Runs
		}
		runs := make([]MaintenanceRun, 0, len(history.Runs)+1)
		runs = append(runs, run)
		for _, existing := range history.Runs {
			if existing.Uid == run.Uid {
				continue
			}
			runs = append(runs, existing)
		}
		if len(runs) > runHistoryLimit {
			runs = runs[:runHistoryLimit]
		}
		history.Runs = runs

		value, marshalErr := json.Marshal(history)
		if marshalErr != nil {
			return marshalErr
		}
		_, writeErr := shared.RpcGrantStateWrite(client, runHistoryKey, value, record.Revision)
		if writeErr == nil {
			return nil
		}
		if !shared.IsConflict(writeErr) {
			return writeErr
		}
	}
	return errors.New("the maintenance run history changed while recording a run")
}

func runKey(run MaintenanceRun) string {
	if run.Kind == RunKindNodeAction {
		return runNodeKey(run.Effective.Target.Nodes[0])
	}
	return runUpdateKey
}

func runWriteNew(client stackClient, run MaintenanceRun) error {
	key := runKey(run)
	record, err := stackValueRead[MaintenanceRun](client, key, "maintenance run")
	if err != nil {
		return err
	}
	if record.Found {
		return errors.New("a maintenance run already exists")
	}

	value, err := json.Marshal(run)
	if err != nil {
		return err
	}
	_, err = shared.RpcGrantStateWrite(client, key, value, record.Revision)
	return err
}

func runWrite(client stackClient, run MaintenanceRun, revision int64) (int64, error) {
	value, err := json.Marshal(run)
	if err != nil {
		return 0, err
	}
	return shared.RpcGrantStateWrite(client, runKey(run), value, revision)
}

func runDelete(client stackClient, run MaintenanceRun, revision int64) error {
	_, err := shared.RpcGrantStateWrite(client, runKey(run), json.RawMessage("null"), revision)
	return err
}

func findRun(client stackClient, runUid string, nodeName string) (MaintenanceRun, int64, error) {
	nodeGroup := ""
	nodeKnown := false
	if nodeName != "" {
		record, found, err := readClusterRecord()
		if err != nil {
			return MaintenanceRun{}, 0, fmt.Errorf("could not read the cluster record: %s", err)
		}
		if found {
			for _, node := range record.Nodes {
				if node.Hostname == nodeName {
					nodeGroup = node.Group
					nodeKnown = true
					break
				}
			}
		}
	}

	updateRecord, err := runUpdateRead(client)
	if err != nil {
		return MaintenanceRun{}, 0, err
	}
	if updateRecord.Found {
		uidMatch := runUid != "" && updateRecord.Value.Uid == runUid
		nodeMatch := runUid == "" && (nodeName == "" || runLookupTargetsNode(updateRecord.Value, nodeName, nodeGroup, nodeKnown))
		if uidMatch || nodeMatch {
			return updateRecord.Value, updateRecord.Revision, nil
		}
	}

	nodeRuns, err := runNodeList(client)
	if err != nil {
		return MaintenanceRun{}, 0, err
	}
	for _, run := range nodeRuns {
		if runUid != "" {
			if run.Uid != runUid {
				continue
			}
		} else if nodeName != "" && !runLookupTargetsNode(run, nodeName, nodeGroup, nodeKnown) {
			continue
		}
		record, readErr := stackValueRead[MaintenanceRun](client, runNodeKey(run.Effective.Target.Nodes[0]), "maintenance run")
		if readErr != nil {
			return MaintenanceRun{}, 0, readErr
		}
		if record.Found {
			return record.Value, record.Revision, nil
		}
	}
	return MaintenanceRun{}, 0, errRunNotFound
}

func runLookupTargetsNode(run MaintenanceRun, nodeName string, nodeGroup string, nodeKnown bool) bool {
	if nodeKnown {
		return runTargetsNode(run, nodeName, nodeGroup)
	}
	for _, name := range run.Effective.Target.Nodes {
		if name == nodeName {
			return true
		}
	}
	return runContainsNode(run, nodeName)
}

func runContainsNode(run MaintenanceRun, nodeName string) bool {
	for _, task := range run.Tasks {
		for _, item := range task.Items {
			if item.NodeName == nodeName {
				return true
			}
		}
	}
	return false
}

func CancelRun(runUid string, nodeName string) (MaintenanceRun, error) {
	runUid = strings.TrimSpace(runUid)
	nodeName = strings.TrimSpace(nodeName)

	client, err := stackClientNew()
	if err != nil {
		return MaintenanceRun{}, err
	}

	for attempt := 0; attempt < 5; attempt++ {
		run, revision, findErr := findRun(client, runUid, nodeName)
		if findErr != nil {
			if errors.Is(findErr, errRunNotFound) && nodeName != "" {
				if cancelErr := cancelActiveOperation(nodeName); cancelErr != nil {
					return MaintenanceRun{}, cancelErr
				}
				return MaintenanceRun{Kind: RunKindNodeAction, Phase: RunPending, CancelRequested: true}, nil
			}
			return MaintenanceRun{}, findErr
		}
		if RunTerminal(run) {
			if nodeName != "" && runUid == "" {
				if cancelErr := cancelActiveOperation(nodeName); cancelErr == nil {
					return MaintenanceRun{Kind: RunKindNodeAction, Phase: RunPending, CancelRequested: true}, nil
				}
			}
			return MaintenanceRun{}, errors.New("the maintenance run already finished")
		}

		run.CancelRequested = true
		if (run.Phase == RunFailed || run.Phase == RunBlocked) && run.ClaimUid != "" {
			run.ClaimUid = ""
			run.ClaimDeadline = time.Time{}
		} else if run.ClaimUid != "" && !time.Now().UTC().Before(run.ClaimDeadline) {
			run.ClaimUid = ""
			run.ClaimDeadline = time.Time{}
		}
		_, err = runWrite(client, run, revision)
		if err == nil {
			maintenanceWakeSignal()
			return run, nil
		}
		if !shared.IsConflict(err) {
			return MaintenanceRun{}, err
		}
	}
	return MaintenanceRun{}, errors.New("the maintenance run changed while cancelling it. Try again")
}

func RetryRun(runUid string, nodeName string) (MaintenanceRun, error) {
	runUid = strings.TrimSpace(runUid)
	nodeName = strings.TrimSpace(nodeName)

	client, err := stackClientNew()
	if err != nil {
		return MaintenanceRun{}, err
	}

	for attempt := 0; attempt < 5; attempt++ {
		run, revision, findErr := findRun(client, runUid, nodeName)
		if findErr != nil {
			return MaintenanceRun{}, findErr
		}
		if RunTerminal(run) {
			return MaintenanceRun{}, errors.New("the maintenance run already finished")
		}
		if run.Phase != RunBlocked && run.Phase != RunFailed {
			return MaintenanceRun{}, errors.New("only a blocked or failed maintenance run can be retried")
		}

		run.Phase = RunPending
		run.Message = ""
		run.Error = ""
		run.CancelRequested = false
		run.BackupRequestUid = ""
		run.ClaimUid = ""
		run.ClaimDeadline = time.Time{}
		for taskIndex := range run.Tasks {
			task := &run.Tasks[taskIndex]
			if task.Phase == RunCompleted {
				continue
			}
			taskRunning := false
			for itemIndex := range task.Items {
				item := &task.Items[itemIndex]
				switch item.Phase {
				case RunCompleted:
					continue
				case RunRunning:
					taskRunning = true
				default:
					item.Phase = RunPending
					item.OperationUid = ""
					item.Error = ""
					item.StartedAt = time.Time{}
				}
			}
			if taskRunning {
				task.Phase = RunRunning
			} else {
				task.Phase = RunPending
			}
		}

		_, err = runWrite(client, run, revision)
		if err == nil {
			maintenanceWakeSignal()
			return run, nil
		}
		if !shared.IsConflict(err) {
			return MaintenanceRun{}, err
		}
	}
	return MaintenanceRun{}, errors.New("the maintenance run changed while retrying it. Try again")
}

func ReadStatus() (RunStatus, error) {
	client, err := stackClientNew()
	if err != nil {
		return RunStatus{}, err
	}

	policyRecord, err := PolicyRead(client)
	if err != nil {
		return RunStatus{}, err
	}
	scheduleRecord, err := ScheduleRead(client)
	if err != nil {
		return RunStatus{}, err
	}

	active, err := activeRuns(client)
	if err != nil {
		return RunStatus{}, err
	}

	lastRecord, err := runLastRead(client)
	if err != nil {
		return RunStatus{}, err
	}

	historyRecord, err := runHistoryRead(client)
	if err != nil {
		return RunStatus{}, err
	}

	status := RunStatus{
		PolicyFound: policyRecord.Found,
		Policy:      DefaultPolicy(),
		Schedule:    scheduleRecord.Value,
		ActiveRuns:  active,
		RecentRuns:  historyRecord.Value.Runs,
	}
	if policyRecord.Found {
		status.Policy = PolicyNormalize(policyRecord.Value)
	}
	if lastRecord.Found {
		last := lastRecord.Value
		status.LastRun = &last
	}
	return status, nil
}

func activeRuns(client stackClient) ([]MaintenanceRun, error) {
	runs := []MaintenanceRun{}
	updateRecord, err := runUpdateRead(client)
	if err != nil {
		return nil, err
	}
	if updateRecord.Found {
		runs = append(runs, updateRecord.Value)
	}
	nodeRuns, err := runNodeList(client)
	if err != nil {
		return nil, err
	}
	runs = append(runs, nodeRuns...)
	return runs, nil
}

func RunsSnapshot() ([]MaintenanceRun, error) {
	client, err := stackClientNew()
	if err != nil {
		return nil, err
	}
	return activeRuns(client)
}
