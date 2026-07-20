package ucx_syncthing

import (
	"math"
	"sort"
	"strings"
	"sync"
	"time"
)

const (
	stateSchemaVersion = 1
	maxFolders         = 2048
	maxDevices         = 2048
	maxEnrichmentItems = 32
	maxFolderErrors    = 20
	maxErrorLength     = 1024
	maxLabelLength     = 256
)

const (
	healthHealthy            = "healthy"
	healthTemporarilyFailing = "temporarily_failing"
	healthRestartRequired    = "restart_required"
)

type Snapshot struct {
	SchemaVersion int
	GeneratedAt   string
	Syncthing     SyncthingState
	Health        HealthState
	Telemetry     TelemetryState
	Folders       []FolderState
	Devices       []DeviceState
	Truncated     bool
}

type SyncthingState struct {
	Version   string
	DeviceID  string
	StartedAt string
}

type HealthState struct {
	State               string
	CheckedAt           string
	LastSuccessAt       string
	FailureSince        string
	ConsecutiveFailures int
	Error               string
}

type TelemetryState struct {
	LastEventAt          string
	LastConnectionPollAt string
	LastStatsPollAt      string
	LastConfigRefreshAt  string
	LastMetricsPollAt    string
	Stale                bool
	Error                string
}

type FolderState struct {
	ID              string
	Label           string
	State           string
	StateChanged    string
	LastScan        string
	OutOfSyncItems  int64
	OutOfSyncBytes  int64
	ErrorCount      int
	Error           string
	WatchError      string
	Errors          []FolderError
	ErrorsTruncated bool
	UpdatedAt       string
}

type FolderError struct {
	Path    string
	Message string
}

type DeviceState struct {
	ID                    string
	Label                 string
	Online                bool
	Paused                bool
	LastSeen              string
	SendBytesPerSecond    float64
	ReceiveBytesPerSecond float64
	RateKnown             bool
	Completion            CompletionState
	LastDisconnectReason  string
	ConnectionError       string
	UpdatedAt             string
}

type CompletionState struct {
	Known       bool
	Percent     float64
	GlobalBytes int64
	NeedBytes   int64
	GlobalItems int64
	NeedItems   int64
	NeedDeletes int64
	RemoteState string
	UpdatedAt   string
	Error       string
}

// MetricsSnapshot is deliberately fixed-shape and identifier-free. A later
// publication step can consume it without deriving fleet metrics from UI data.
type MetricsSnapshot struct {
	SchemaVersion            int
	PreparedAt               string
	HealthyInstances         int64
	DegradedInstances        int64
	RestartRequiredInstances int64
	OnlineDevices            int64
	OfflineDevices           int64
	OutOfSyncItems           int64
	OutOfSyncBytes           int64
	SendBytesPerSecond       float64
	ReceiveBytesPerSecond    float64
	FoldersWithErrors        int64
	DevicesWithErrors        int64
	TelemetryStale           bool
	Truncated                bool
}

type rateSample struct {
	observedAt time.Time
	startedAt  time.Time
	inBytes    int64
	outBytes   int64
	connected  bool
}

type stateStore struct {
	mu              sync.RWMutex
	snapshot        Snapshot
	metrics         MetricsSnapshot
	folders         map[string]*FolderState
	devices         map[string]*DeviceState
	rateSamples     map[string]rateSample
	telemetryErrors map[string]string
	subscribers     map[int]chan struct{}
	nextSubID       int
}

func newStateStore() *stateStore {
	now := formatTime(time.Now())
	s := &stateStore{
		folders:         map[string]*FolderState{},
		devices:         map[string]*DeviceState{},
		rateSamples:     map[string]rateSample{},
		telemetryErrors: map[string]string{"collector": "Telemetry is initializing"},
		subscribers:     map[int]chan struct{}{},
	}
	s.snapshot = Snapshot{
		SchemaVersion: stateSchemaVersion,
		GeneratedAt:   now,
		Health:        HealthState{State: healthTemporarilyFailing, Error: "Waiting for Syncthing"},
		Telemetry:     TelemetryState{Stale: true, Error: "Telemetry is initializing"},
		Folders:       []FolderState{},
		Devices:       []DeviceState{},
	}
	s.rebuildLocked(time.Now())
	return s
}

func (s *stateStore) read() Snapshot {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return cloneSnapshot(s.snapshot)
}

func (s *stateStore) readMetrics() MetricsSnapshot {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.metrics
}

func (s *stateStore) update(fn func(*stateStore)) {
	s.mu.Lock()
	fn(s)
	s.rebuildLocked(time.Now())
	for _, ch := range s.subscribers {
		select {
		case ch <- struct{}{}:
		default:
		}
	}
	s.mu.Unlock()
}

func (s *stateStore) subscribe() (int, <-chan struct{}) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.nextSubID++
	id := s.nextSubID
	ch := make(chan struct{}, 1)
	s.subscribers[id] = ch
	return id, ch
}

func (s *stateStore) unsubscribe(id int) {
	s.mu.Lock()
	delete(s.subscribers, id)
	s.mu.Unlock()
}

func (s *stateStore) rebuildLocked(now time.Time) {
	errorSources := make([]string, 0, len(s.telemetryErrors))
	for source := range s.telemetryErrors {
		errorSources = append(errorSources, source)
	}
	sort.Strings(errorSources)
	telemetryErrors := make([]string, 0, len(errorSources))
	for _, source := range errorSources {
		telemetryErrors = append(telemetryErrors, source+": "+s.telemetryErrors[source])
	}
	s.snapshot.Telemetry.Stale = len(telemetryErrors) > 0
	s.snapshot.Telemetry.Error = bounded(strings.Join(telemetryErrors, "; "), maxErrorLength)

	folderIDs := make([]string, 0, len(s.folders))
	for id := range s.folders {
		folderIDs = append(folderIDs, id)
	}
	sort.Strings(folderIDs)
	deviceIDs := make([]string, 0, len(s.devices))
	for id := range s.devices {
		deviceIDs = append(deviceIDs, id)
	}
	sort.Strings(deviceIDs)

	s.snapshot.GeneratedAt = formatTime(now)
	s.snapshot.Folders = make([]FolderState, 0, len(folderIDs))
	for _, id := range folderIDs {
		folder := *s.folders[id]
		folder.Errors = append([]FolderError(nil), folder.Errors...)
		s.snapshot.Folders = append(s.snapshot.Folders, folder)
	}
	s.snapshot.Devices = make([]DeviceState, 0, len(deviceIDs))
	for _, id := range deviceIDs {
		s.snapshot.Devices = append(s.snapshot.Devices, *s.devices[id])
	}
	s.metrics = prepareMetricsSnapshot(s.snapshot, now)
}

func prepareMetricsSnapshot(snapshot Snapshot, now time.Time) MetricsSnapshot {
	result := MetricsSnapshot{
		SchemaVersion:  stateSchemaVersion,
		PreparedAt:     formatTime(now),
		TelemetryStale: snapshot.Telemetry.Stale,
		Truncated:      snapshot.Truncated,
	}
	switch snapshot.Health.State {
	case healthHealthy:
		result.HealthyInstances = 1
	case healthRestartRequired:
		result.RestartRequiredInstances = 1
	default:
		result.DegradedInstances = 1
	}
	for _, folder := range snapshot.Folders {
		result.OutOfSyncItems = saturatingAdd(result.OutOfSyncItems, folder.OutOfSyncItems)
		result.OutOfSyncBytes = saturatingAdd(result.OutOfSyncBytes, folder.OutOfSyncBytes)
		if folder.ErrorCount > 0 || folder.Error != "" || folder.WatchError != "" {
			result.FoldersWithErrors++
		}
	}
	for _, device := range snapshot.Devices {
		if device.Online {
			result.OnlineDevices++
		} else {
			result.OfflineDevices++
		}
		if device.ConnectionError != "" || device.LastDisconnectReason != "" || device.Completion.Error != "" {
			result.DevicesWithErrors++
		}
		if device.RateKnown && finite(device.SendBytesPerSecond) && finite(device.ReceiveBytesPerSecond) {
			result.SendBytesPerSecond += device.SendBytesPerSecond
			result.ReceiveBytesPerSecond += device.ReceiveBytesPerSecond
		}
	}
	return result
}

func cloneSnapshot(snapshot Snapshot) Snapshot {
	result := snapshot
	result.Folders = append([]FolderState(nil), snapshot.Folders...)
	for i := range result.Folders {
		result.Folders[i].Errors = append([]FolderError(nil), snapshot.Folders[i].Errors...)
	}
	result.Devices = append([]DeviceState(nil), snapshot.Devices...)
	return result
}

func saturatingAdd(a int64, b int64) int64 {
	if b > 0 && a > math.MaxInt64-b {
		return math.MaxInt64
	}
	if b < 0 && a < math.MinInt64-b {
		return math.MinInt64
	}
	return a + b
}

func finite(value float64) bool {
	return !math.IsNaN(value) && !math.IsInf(value, 0)
}

func bounded(value string, max int) string {
	if len(value) <= max {
		return value
	}
	return value[:max]
}

func formatTime(value time.Time) string {
	if value.IsZero() {
		return ""
	}
	return value.UTC().Format(time.RFC3339Nano)
}
