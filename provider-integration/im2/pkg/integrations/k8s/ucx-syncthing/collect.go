package ucx_syncthing

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"math"
	"net/url"
	"os"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"ucloud.dk/shared/pkg/log"
)

const eventTypes = "DeviceConnected,DeviceDisconnected,StateChanged,FolderSummary,FolderCompletion,FolderErrors,ConfigSaved,FolderPaused,FolderResumed,StartupComplete"

type configDevice struct {
	ID     string `json:"deviceID"`
	Name   string `json:"name"`
	Paused bool   `json:"paused"`
}

type configFolder struct {
	ID     string `json:"id"`
	Label  string `json:"label"`
	Paused bool   `json:"paused"`
}

type connectionSnapshot struct {
	Connections map[string]connectionInfo `json:"connections"`
}

type connectionInfo struct {
	Connected     bool      `json:"connected"`
	Paused        bool      `json:"paused"`
	InBytesTotal  int64     `json:"inBytesTotal"`
	OutBytesTotal int64     `json:"outBytesTotal"`
	StartedAt     time.Time `json:"startedAt"`
}

type completionResponse struct {
	Completion  float64 `json:"completion"`
	GlobalBytes int64   `json:"globalBytes"`
	NeedBytes   int64   `json:"needBytes"`
	GlobalItems int64   `json:"globalItems"`
	NeedItems   int64   `json:"needItems"`
	NeedDeletes int64   `json:"needDeletes"`
	RemoteState string  `json:"remoteState"`
}

type syncthingEvent struct {
	ID   int             `json:"id"`
	Time time.Time       `json:"time"`
	Type string          `json:"type"`
	Data json.RawMessage `json:"data"`
}

type folderSummary struct {
	Errors         int       `json:"errors"`
	NeedBytes      int64     `json:"needBytes"`
	NeedTotalItems int64     `json:"needTotalItems"`
	State          string    `json:"state"`
	StateChanged   time.Time `json:"stateChanged"`
	Error          string    `json:"error"`
	WatchError     string    `json:"watchError"`
}

type parsedFolderMetrics struct {
	state     string
	needItems int64
	needBytes int64
}

func runCollector(ctx context.Context, cfg runtimeConfig, api *apiRuntime, store *stateStore) {
	go runHealthLoop(ctx, cfg, api, store)

	for ctx.Err() == nil {
		if err := api.loadAPIKey(); err == nil {
			setTelemetryStatus(store, "credentials", nil)
			break
		} else {
			setTelemetryStatus(store, "credentials", err)
		}
		if !sleepContext(ctx, time.Second) {
			return
		}
	}

	reconcile := make(chan struct{}, 1)
	cursor := eventHighWater(ctx, api)
	collectReconciliation(ctx, api, store)
	setTelemetryStatus(store, "collector", nil)
	go runConnectionLoop(ctx, api, store)
	go runEventLoop(ctx, api, store, reconcile, cursor)

	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			collectReconciliation(ctx, api, store)
		case <-reconcile:
			collectReconciliation(ctx, api, store)
		}
	}
}

func collectReconciliation(ctx context.Context, api *apiRuntime, store *stateStore) {
	collectSystemInfo(ctx, api, store)
	if err := collectConfiguration(ctx, api, store); err != nil {
		setTelemetryStatus(store, "configuration", err)
	}
	if err := collectMetrics(ctx, api, store); err != nil {
		setTelemetryStatus(store, "metrics", err)
	}
	if err := collectStats(ctx, api, store); err != nil {
		setTelemetryStatus(store, "statistics", err)
	}
	collectCompletions(ctx, api, store)
	collectFolderErrors(ctx, api, store)
}

func collectSystemInfo(ctx context.Context, api *apiRuntime, store *stateStore) {
	var status struct {
		DeviceID  string    `json:"myID"`
		StartTime time.Time `json:"startTime"`
	}
	var version struct {
		Version string `json:"version"`
	}
	statusErr := apiFetchJson(ctx, api, "/rest/system/status", nil, &status)
	versionErr := apiFetchJson(ctx, api, "/rest/system/version", nil, &version)
	if statusErr != nil {
		setTelemetryStatus(store, "system", statusErr)
		return
	}
	store.update(func(s *stateStore) {
		if s.snapshot.Syncthing.StartedAt != "" && s.snapshot.Syncthing.StartedAt != formatTime(status.StartTime) {
			s.rateSamples = map[string]rateSample{}
		}
		s.snapshot.Syncthing.DeviceID = status.DeviceID
		s.snapshot.Syncthing.StartedAt = formatTime(status.StartTime)
		if versionErr == nil {
			s.snapshot.Syncthing.Version = version.Version
		}
	})
	setTelemetryStatus(store, "system", versionErr)
}

func collectConfiguration(ctx context.Context, api *apiRuntime, store *stateStore) error {
	var devices []configDevice
	var folders []configFolder
	if err := apiFetchJson(ctx, api, "/rest/config/devices", nil, &devices); err != nil {
		return err
	}
	if err := apiFetchJson(ctx, api, "/rest/config/folders", nil, &folders); err != nil {
		return err
	}
	now := time.Now()
	store.update(func(s *stateStore) {
		localID := s.snapshot.Syncthing.DeviceID
		nextFolders := make(map[string]*FolderState, min(len(folders), maxFolders))
		for _, config := range folders {
			if config.ID == "" || len(nextFolders) >= maxFolders {
				continue
			}
			folder := s.folders[config.ID]
			if folder == nil {
				folder = &FolderState{ID: config.ID, State: "unknown"}
			}
			folder.Label = bounded(config.Label, maxLabelLength)
			if folder.Label == "" {
				folder.Label = bounded(config.ID, maxLabelLength)
			}
			if config.Paused {
				folder.State = "paused"
			} else if folder.State == "paused" {
				folder.State = "unknown"
			}
			nextFolders[config.ID] = folder
		}
		nextDevices := make(map[string]*DeviceState, min(len(devices), maxDevices))
		for _, config := range devices {
			if config.ID == "" || config.ID == localID || len(nextDevices) >= maxDevices {
				continue
			}
			device := s.devices[config.ID]
			if device == nil {
				device = &DeviceState{ID: config.ID}
			}
			device.Label = bounded(config.Name, maxLabelLength)
			if device.Label == "" {
				device.Label = bounded(config.ID, maxLabelLength)
			}
			device.Paused = config.Paused
			nextDevices[config.ID] = device
		}
		s.folders = nextFolders
		s.devices = nextDevices
		for id := range s.rateSamples {
			if s.devices[id] == nil {
				delete(s.rateSamples, id)
			}
		}
		s.snapshot.Truncated = len(folders) > maxFolders || len(devices) > maxDevices
		s.snapshot.Telemetry.LastConfigRefreshAt = formatTime(now)
	})
	setTelemetryStatus(store, "configuration", nil)
	return nil
}

func collectMetrics(ctx context.Context, api *apiRuntime, store *stateStore) error {
	data, err := apiFetchMetrics(ctx, api)
	if err != nil {
		return err
	}
	metrics := parseFolderMetrics(data)
	now := time.Now()
	store.update(func(s *stateStore) {
		for id, metric := range metrics {
			folder := s.folders[id]
			if folder == nil {
				continue
			}
			if metric.state != "" && folder.State != "paused" {
				folder.State = metric.state
			}
			folder.OutOfSyncItems = metric.needItems
			folder.OutOfSyncBytes = metric.needBytes
			folder.UpdatedAt = formatTime(now)
		}
		s.snapshot.Telemetry.LastMetricsPollAt = formatTime(now)
	})
	setTelemetryStatus(store, "metrics", nil)
	return nil
}

func parseFolderMetrics(data []byte) map[string]parsedFolderMetrics {
	result := map[string]parsedFolderMetrics{}
	scanner := bufio.NewScanner(strings.NewReader(string(data)))
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "syncthing_model_folder_state{") {
			labels, value, ok := parseMetric(line)
			if !ok {
				continue
			}
			stateNumber, err := strconv.Atoi(value)
			if err != nil || stateNumber < 0 || stateNumber >= len(folderMetricStates) {
				continue
			}
			metric := result[labels["folder"]]
			metric.state = folderMetricStates[stateNumber]
			result[labels["folder"]] = metric
		} else if strings.HasPrefix(line, "syncthing_model_folder_summary{") {
			labels, value, ok := parseMetric(line)
			if !ok || labels["scope"] != "need" {
				continue
			}
			parsed, err := strconv.ParseFloat(value, 64)
			if err != nil || parsed < 0 || !finite(parsed) || parsed >= float64(math.MaxInt64) {
				continue
			}
			metric := result[labels["folder"]]
			switch labels["type"] {
			case "bytes":
				metric.needBytes = int64(parsed)
			case "files", "directories", "symlinks", "deleted":
				metric.needItems = saturatingAdd(metric.needItems, int64(parsed))
			}
			result[labels["folder"]] = metric
		}
	}
	return result
}

var folderMetricStates = []string{"idle", "scanning", "scan-waiting", "sync-waiting", "sync-preparing", "syncing", "cleaning", "clean-waiting", "error"}

func parseMetric(line string) (map[string]string, string, bool) {
	open := strings.IndexByte(line, '{')
	close := strings.LastIndex(line, "} ")
	if open < 0 || close < open {
		return nil, "", false
	}
	labels := map[string]string{}
	for _, part := range splitMetricLabels(line[open+1 : close]) {
		key, raw, ok := strings.Cut(part, "=")
		if !ok {
			return nil, "", false
		}
		value, err := strconv.Unquote(raw)
		if err != nil {
			return nil, "", false
		}
		labels[key] = value
	}
	if labels["folder"] == "" {
		return nil, "", false
	}
	return labels, strings.TrimSpace(line[close+2:]), true
}

func splitMetricLabels(value string) []string {
	result := []string{}
	start := 0
	inQuotes := false
	escaped := false
	for index, ch := range value {
		if escaped {
			escaped = false
			continue
		}
		if ch == '\\' && inQuotes {
			escaped = true
			continue
		}
		if ch == '"' {
			inQuotes = !inQuotes
			continue
		}
		if ch == ',' && !inQuotes {
			result = append(result, value[start:index])
			start = index + 1
		}
	}
	return append(result, value[start:])
}

func collectStats(ctx context.Context, api *apiRuntime, store *stateStore) error {
	var deviceStats map[string]struct {
		LastSeen time.Time `json:"lastSeen"`
	}
	var folderStats map[string]struct {
		LastScan time.Time `json:"lastScan"`
	}
	if err := apiFetchJson(ctx, api, "/rest/stats/device", nil, &deviceStats); err != nil {
		return err
	}
	if err := apiFetchJson(ctx, api, "/rest/stats/folder", nil, &folderStats); err != nil {
		return err
	}
	now := time.Now()
	store.update(func(s *stateStore) {
		for id, stats := range deviceStats {
			if device := s.devices[id]; device != nil {
				device.LastSeen = formatTime(stats.LastSeen)
			}
		}
		for id, stats := range folderStats {
			if folder := s.folders[id]; folder != nil {
				folder.LastScan = formatTime(stats.LastScan)
			}
		}
		s.snapshot.Telemetry.LastStatsPollAt = formatTime(now)
	})
	setTelemetryStatus(store, "statistics", nil)
	return nil
}

func collectCompletions(ctx context.Context, api *apiRuntime, store *stateStore) {
	snapshot := store.read()
	var wg sync.WaitGroup
	var failures atomic.Int64
	workers := make(chan struct{}, 4)
	for index, device := range snapshot.Devices {
		if index >= maxEnrichmentItems {
			store.update(func(s *stateStore) { s.snapshot.Truncated = true })
			break
		}
		wg.Add(1)
		go func(device DeviceState) {
			defer wg.Done()
			select {
			case workers <- struct{}{}:
				defer func() { <-workers }()
			case <-ctx.Done():
				return
			}
			var completion completionResponse
			err := apiFetchJson(ctx, api, "/rest/db/completion", url.Values{"device": []string{device.ID}}, &completion)
			if err != nil {
				failures.Add(1)
			}
			now := time.Now()
			store.update(func(s *stateStore) {
				current := s.devices[device.ID]
				if current == nil {
					return
				}
				if err != nil || !finite(completion.Completion) {
					if err == nil {
						err = fmt.Errorf("completion is not finite")
						failures.Add(1)
					}
					current.Completion.Error = bounded(errorString(err), maxErrorLength)
					return
				}
				current.Completion = CompletionState{
					Known: true, Percent: completion.Completion,
					GlobalBytes: completion.GlobalBytes, NeedBytes: completion.NeedBytes,
					GlobalItems: completion.GlobalItems, NeedItems: completion.NeedItems,
					NeedDeletes: completion.NeedDeletes, RemoteState: completion.RemoteState,
					UpdatedAt: formatTime(now),
				}
			})
		}(device)
	}
	wg.Wait()
	if failures.Load() > 0 {
		setTelemetryStatus(store, "completion", fmt.Errorf("%d completion requests failed", failures.Load()))
	} else {
		setTelemetryStatus(store, "completion", nil)
	}
}

func collectFolderErrors(ctx context.Context, api *apiRuntime, store *stateStore) {
	snapshot := store.read()
	var wg sync.WaitGroup
	var failures atomic.Int64
	workers := make(chan struct{}, 4)
	for index, folder := range snapshot.Folders {
		if index >= maxEnrichmentItems {
			store.update(func(s *stateStore) { s.snapshot.Truncated = true })
			break
		}
		wg.Add(1)
		go func(folder FolderState) {
			defer wg.Done()
			select {
			case workers <- struct{}{}:
				defer func() { <-workers }()
			case <-ctx.Done():
				return
			}
			var response struct {
				Errors []struct {
					Path  string `json:"path"`
					Error string `json:"error"`
				} `json:"errors"`
			}
			err := apiFetchJson(ctx, api, "/rest/folder/errors", url.Values{
				"folder": []string{folder.ID}, "page": []string{"1"}, "perpage": []string{strconv.Itoa(maxFolderErrors + 1)},
			}, &response)
			if err == nil {
				store.update(func(s *stateStore) {
					current := s.folders[folder.ID]
					if current == nil {
						return
					}
					current.ErrorsTruncated = len(response.Errors) > maxFolderErrors
					if !current.ErrorsTruncated {
						current.ErrorCount = len(response.Errors)
					} else if len(response.Errors) > current.ErrorCount {
						current.ErrorCount = len(response.Errors)
					}
					current.Errors = current.Errors[:0]
					for i, item := range response.Errors {
						if i >= maxFolderErrors {
							break
						}
						current.Errors = append(current.Errors, FolderError{Path: bounded(item.Path, maxErrorLength), Message: bounded(item.Error, maxErrorLength)})
					}
				})
			} else {
				failures.Add(1)
			}
		}(folder)
	}
	wg.Wait()
	if failures.Load() > 0 {
		setTelemetryStatus(store, "folder errors", fmt.Errorf("%d folder error requests failed", failures.Load()))
	} else {
		setTelemetryStatus(store, "folder errors", nil)
	}
}

func runConnectionLoop(ctx context.Context, api *apiRuntime, store *stateStore) {
	for {
		collectConnections(ctx, api, store, time.Now())
		if !sleepContext(ctx, 2*time.Second) {
			return
		}
	}
}

func collectConnections(ctx context.Context, api *apiRuntime, store *stateStore, observedAt time.Time) {
	var response connectionSnapshot
	if err := apiFetchJson(ctx, api, "/rest/system/connections", nil, &response); err != nil {
		setTelemetryStatus(store, "connections", err)
		return
	}
	store.update(func(s *stateStore) {
		applyConnections(s, response, observedAt)
		s.snapshot.Telemetry.LastConnectionPollAt = formatTime(observedAt)
	})
	setTelemetryStatus(store, "connections", nil)
}

func applyConnections(store *stateStore, response connectionSnapshot, observedAt time.Time) {
	for id, device := range store.devices {
		connection, found := response.Connections[id]
		if !found {
			connection = connectionInfo{}
		}
		previous, hadPrevious := store.rateSamples[id]
		device.Online = connection.Connected
		if connection.Connected {
			device.LastDisconnectReason = ""
			device.ConnectionError = ""
		}
		device.Paused = connection.Paused
		device.UpdatedAt = formatTime(observedAt)
		device.RateKnown = false
		device.SendBytesPerSecond = 0
		device.ReceiveBytesPerSecond = 0
		elapsed := observedAt.Sub(previous.observedAt).Seconds()
		if hadPrevious && previous.connected && connection.Connected && elapsed > 0 &&
			previous.startedAt.Equal(connection.StartedAt) && connection.InBytesTotal >= previous.inBytes && connection.OutBytesTotal >= previous.outBytes {
			device.ReceiveBytesPerSecond = float64(connection.InBytesTotal-previous.inBytes) / elapsed
			device.SendBytesPerSecond = float64(connection.OutBytesTotal-previous.outBytes) / elapsed
			device.RateKnown = finite(device.ReceiveBytesPerSecond) && finite(device.SendBytesPerSecond)
		}
		store.rateSamples[id] = rateSample{
			observedAt: observedAt, startedAt: connection.StartedAt,
			inBytes: connection.InBytesTotal, outBytes: connection.OutBytesTotal, connected: connection.Connected,
		}
	}
}

func eventHighWater(ctx context.Context, api *apiRuntime) int {
	var latest []syncthingEvent
	if err := apiFetchEvents(ctx, api, url.Values{"events": []string{eventTypes}, "limit": []string{"1"}, "timeout": []string{"0"}}, &latest); err == nil && len(latest) > 0 {
		return latest[len(latest)-1].ID
	}
	return 0
}

func runEventLoop(ctx context.Context, api *apiRuntime, store *stateStore, reconcile chan<- struct{}, cursor int) {
	for ctx.Err() == nil {
		var events []syncthingEvent
		err := apiFetchEvents(ctx, api, url.Values{
			"events": []string{eventTypes}, "since": []string{strconv.Itoa(cursor)}, "limit": []string{"128"}, "timeout": []string{"60"},
		}, &events)
		if err != nil {
			if ctx.Err() == nil {
				setTelemetryStatus(store, "events", err)
				sleepContext(ctx, time.Second)
			}
			continue
		}
		setTelemetryStatus(store, "events", nil)
		for _, event := range events {
			if event.ID <= cursor {
				continue
			}
			if cursor != 0 && event.ID != cursor+1 {
				select {
				case reconcile <- struct{}{}:
				default:
				}
			}
			cursor = event.ID
			applyEvent(store, event, reconcile)
		}
	}
}

func applyEvent(store *stateStore, event syncthingEvent, reconcile chan<- struct{}) {
	now := event.Time
	if now.IsZero() {
		now = time.Now()
	}
	switch event.Type {
	case "StateChanged":
		var data struct {
			Folder string `json:"folder"`
			To     string `json:"to"`
			Error  string `json:"error"`
		}
		if json.Unmarshal(event.Data, &data) == nil {
			store.update(func(s *stateStore) {
				if folder := s.folders[data.Folder]; folder != nil {
					folder.State = data.To
					folder.StateChanged = formatTime(now)
					folder.Error = bounded(data.Error, maxErrorLength)
					folder.UpdatedAt = formatTime(now)
				}
				s.snapshot.Telemetry.LastEventAt = formatTime(now)
			})
		}
	case "FolderSummary":
		var data struct {
			Folder  string        `json:"folder"`
			Summary folderSummary `json:"summary"`
		}
		if json.Unmarshal(event.Data, &data) == nil {
			store.update(func(s *stateStore) {
				if folder := s.folders[data.Folder]; folder != nil {
					folder.State = data.Summary.State
					folder.StateChanged = formatTime(data.Summary.StateChanged)
					folder.OutOfSyncItems = data.Summary.NeedTotalItems
					folder.OutOfSyncBytes = data.Summary.NeedBytes
					folder.ErrorCount = data.Summary.Errors
					folder.Error = bounded(data.Summary.Error, maxErrorLength)
					folder.WatchError = bounded(data.Summary.WatchError, maxErrorLength)
					if data.Summary.Errors == 0 {
						folder.Errors = nil
						folder.ErrorsTruncated = false
					}
					folder.UpdatedAt = formatTime(now)
				}
				s.snapshot.Telemetry.LastEventAt = formatTime(now)
			})
		}
	case "FolderErrors":
		var data struct {
			Folder string `json:"folder"`
			Errors []struct {
				Path  string `json:"path"`
				Error string `json:"error"`
			} `json:"errors"`
		}
		if json.Unmarshal(event.Data, &data) == nil {
			store.update(func(s *stateStore) {
				if folder := s.folders[data.Folder]; folder != nil {
					folder.ErrorCount = len(data.Errors)
					folder.ErrorsTruncated = len(data.Errors) > maxFolderErrors
					folder.Errors = nil
					for i, item := range data.Errors {
						if i >= maxFolderErrors {
							break
						}
						folder.Errors = append(folder.Errors, FolderError{Path: bounded(item.Path, maxErrorLength), Message: bounded(item.Error, maxErrorLength)})
					}
				}
				s.snapshot.Telemetry.LastEventAt = formatTime(now)
			})
		}
	case "DeviceConnected", "DeviceDisconnected":
		var data struct {
			ID    string `json:"id"`
			Error string `json:"error"`
		}
		if json.Unmarshal(event.Data, &data) == nil {
			store.update(func(s *stateStore) {
				if device := s.devices[data.ID]; device != nil {
					device.Online = event.Type == "DeviceConnected"
					device.UpdatedAt = formatTime(now)
					if device.Online {
						device.LastDisconnectReason = ""
						device.ConnectionError = ""
					} else {
						device.RateKnown = false
						device.SendBytesPerSecond = 0
						device.ReceiveBytesPerSecond = 0
						device.LastDisconnectReason = bounded(data.Error, maxErrorLength)
					}
				}
				s.snapshot.Telemetry.LastEventAt = formatTime(now)
			})
		}
	case "ConfigSaved", "StartupComplete", "FolderCompletion", "FolderPaused", "FolderResumed":
		store.update(func(s *stateStore) { s.snapshot.Telemetry.LastEventAt = formatTime(now) })
		select {
		case reconcile <- struct{}{}:
		default:
		}
	}
}

func runHealthLoop(ctx context.Context, cfg runtimeConfig, api *apiRuntime, store *stateStore) {
	var failureSince time.Time
	failures := 0
	restartRequested := false
	for {
		checkedAt := time.Now()
		probeCtx, cancel := context.WithTimeout(ctx, min(cfg.healthInterval, 2*time.Second))
		err := apiProbeHealth(probeCtx, api)
		cancel()
		if err == nil {
			failureSince = time.Time{}
			failures = 0
			store.update(func(s *stateStore) {
				s.snapshot.Health = HealthState{State: healthHealthy, CheckedAt: formatTime(checkedAt), LastSuccessAt: formatTime(checkedAt)}
			})
			restartRequested = false
		} else if ctx.Err() == nil {
			failures++
			if failureSince.IsZero() {
				failureSince = checkedAt
			}
			state := healthTemporarilyFailing
			if checkedAt.Sub(failureSince) >= cfg.healthGrace {
				state = healthRestartRequired
			}
			store.update(func(s *stateStore) {
				lastSuccess := s.snapshot.Health.LastSuccessAt
				s.snapshot.Health = HealthState{
					State: state, CheckedAt: formatTime(checkedAt), LastSuccessAt: lastSuccess,
					FailureSince: formatTime(failureSince), ConsecutiveFailures: failures, Error: bounded(err.Error(), maxErrorLength),
				}
			})
			if state == healthRestartRequired && !restartRequested {
				restartRequested = true
				requestPodRestart()
			}
		}
		if !sleepContext(ctx, cfg.healthInterval) {
			return
		}
	}
}

func requestPodRestart() {
	if os.Getenv("UCLOUD_UCX_APP_NAME") != "syncthing" {
		log.Warn("UCX Syncthing: restart required, refusing to signal PID 1 outside the managed container")
		return
	}
	log.Warn("UCX Syncthing: local API health grace exceeded; terminating the pod")
	time.Sleep(500 * time.Millisecond)
	if err := syscall.Kill(1, syscall.SIGTERM); err != nil {
		log.Warn("UCX Syncthing: failed to signal PID 1: %v", err)
		os.Exit(1)
	}
}

func setTelemetryStatus(store *stateStore, source string, err error) {
	store.update(func(s *stateStore) {
		if err == nil {
			delete(s.telemetryErrors, source)
		} else {
			s.telemetryErrors[source] = bounded(err.Error(), maxErrorLength)
		}
	})
}

func errorString(err error) string {
	if err == nil {
		return ""
	}
	return err.Error()
}

func sleepContext(ctx context.Context, duration time.Duration) bool {
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}
