package syncthing_metrics

import (
	"math"
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
)

const (
	syncthingSnapshotStaleAfter = 60 * time.Second
	syncthingSnapshotRetention  = 5 * time.Minute
)

type publishedSyncthingSnapshot struct {
	snapshot   Snapshot
	receivedAt time.Time
}

type syncthingSnapshotStore struct {
	mu        sync.RWMutex
	snapshots map[string]publishedSyncthingSnapshot
}

type syncthingFleetMetrics struct {
	healthyInstances     int64
	degradedInstances    int64
	unreachableInstances int64
	onlineDevices        int64
	offlineDevices       int64
	outOfSyncItems       int64
	outOfSyncBytes       int64
	sendRate             float64
	receiveRate          float64
	foldersWithErrors    int64
	devicesWithErrors    int64
	staleSnapshots       int64
	maxSnapshotAge       float64
}

var SyncthingSnapshots = syncthingSnapshotStore{snapshots: map[string]publishedSyncthingSnapshot{}}

func (s *syncthingSnapshotStore) Publish(jobID string, snapshot Snapshot, now time.Time) {
	s.mu.Lock()
	s.snapshots[jobID] = publishedSyncthingSnapshot{snapshot: snapshot, receivedAt: now}
	s.mu.Unlock()
}

func (s *syncthingSnapshotStore) Remove(jobIDs []string) {
	s.mu.Lock()
	for _, jobID := range jobIDs {
		delete(s.snapshots, jobID)
	}
	s.mu.Unlock()
}

func (s *syncthingSnapshotStore) Aggregate(now time.Time) syncthingFleetMetrics {
	s.mu.Lock()
	defer s.mu.Unlock()

	result := syncthingFleetMetrics{}
	for jobID, published := range s.snapshots {
		age := now.Sub(published.receivedAt)
		if age > syncthingSnapshotRetention {
			delete(s.snapshots, jobID)
			continue
		}
		if age.Seconds() > result.maxSnapshotAge {
			result.maxSnapshotAge = age.Seconds()
		}
		if age > syncthingSnapshotStaleAfter {
			result.unreachableInstances++
			result.staleSnapshots++
			continue
		}

		snapshot := published.snapshot
		result.healthyInstances += snapshot.HealthyInstances
		result.degradedInstances += snapshot.DegradedInstances + snapshot.RestartRequiredInstances
		result.onlineDevices += snapshot.OnlineDevices
		result.offlineDevices += snapshot.OfflineDevices
		result.outOfSyncItems += snapshot.OutOfSyncItems
		result.outOfSyncBytes += snapshot.OutOfSyncBytes
		result.sendRate += snapshot.SendBytesPerSecond
		result.receiveRate += snapshot.ReceiveBytesPerSecond
		result.foldersWithErrors += snapshot.FoldersWithErrors
		result.devicesWithErrors += snapshot.DevicesWithErrors
	}
	return result
}

func ValidSyncthingSnapshot(snapshot Snapshot) bool {
	return snapshot.SchemaVersion == SchemaVersion &&
		snapshot.HealthyInstances >= 0 && snapshot.HealthyInstances <= 1 &&
		snapshot.DegradedInstances >= 0 && snapshot.DegradedInstances <= 1 &&
		snapshot.RestartRequiredInstances >= 0 && snapshot.RestartRequiredInstances <= 1 &&
		snapshot.HealthyInstances+snapshot.DegradedInstances+snapshot.RestartRequiredInstances == 1 &&
		snapshot.OnlineDevices >= 0 && snapshot.OfflineDevices >= 0 && snapshot.OutOfSyncItems >= 0 &&
		snapshot.OutOfSyncBytes >= 0 && snapshot.FoldersWithErrors >= 0 && snapshot.DevicesWithErrors >= 0 &&
		snapshot.SendBytesPerSecond >= 0 && snapshot.ReceiveBytesPerSecond >= 0 &&
		!math.IsNaN(snapshot.SendBytesPerSecond) && !math.IsInf(snapshot.SendBytesPerSecond, 0) &&
		!math.IsNaN(snapshot.ReceiveBytesPerSecond) && !math.IsInf(snapshot.ReceiveBytesPerSecond, 0)
}

type syncthingPrometheusCollector struct {
	store *syncthingSnapshotStore
	descs []*prometheus.Desc
}

func newSyncthingPrometheusCollector(store *syncthingSnapshotStore) *syncthingPrometheusCollector {
	return &syncthingPrometheusCollector{store: store, descs: []*prometheus.Desc{
		prometheus.NewDesc("ucloud_syncthing_instances", "Current Syncthing instances by health state.", []string{"state"}, nil),
		prometheus.NewDesc("ucloud_syncthing_online_devices", "Devices currently connected to Syncthing instances.", nil, nil),
		prometheus.NewDesc("ucloud_syncthing_offline_devices", "Devices currently disconnected from Syncthing instances.", nil, nil),
		prometheus.NewDesc("ucloud_syncthing_out_of_sync_items", "Items currently out of sync across Syncthing instances.", nil, nil),
		prometheus.NewDesc("ucloud_syncthing_out_of_sync_bytes", "Bytes currently out of sync across Syncthing instances.", nil, nil),
		prometheus.NewDesc("ucloud_syncthing_send_bytes_per_second", "Current aggregate Syncthing send rate.", nil, nil),
		prometheus.NewDesc("ucloud_syncthing_receive_bytes_per_second", "Current aggregate Syncthing receive rate.", nil, nil),
		prometheus.NewDesc("ucloud_syncthing_folders_with_errors", "Syncthing folders currently reporting errors.", nil, nil),
		prometheus.NewDesc("ucloud_syncthing_devices_with_errors", "Syncthing devices currently reporting errors.", nil, nil),
		prometheus.NewDesc("ucloud_syncthing_stale_snapshots", "Syncthing publisher snapshots older than the freshness threshold.", nil, nil),
		prometheus.NewDesc("ucloud_syncthing_snapshot_max_age_seconds", "Age of the oldest retained Syncthing publisher snapshot.", nil, nil),
	}}
}

func (c *syncthingPrometheusCollector) Describe(ch chan<- *prometheus.Desc) {
	for _, desc := range c.descs {
		ch <- desc
	}
}

func (c *syncthingPrometheusCollector) Collect(ch chan<- prometheus.Metric) {
	m := c.store.Aggregate(time.Now())
	ch <- prometheus.MustNewConstMetric(c.descs[0], prometheus.GaugeValue, float64(m.healthyInstances), "healthy")
	ch <- prometheus.MustNewConstMetric(c.descs[0], prometheus.GaugeValue, float64(m.degradedInstances), "degraded")
	ch <- prometheus.MustNewConstMetric(c.descs[0], prometheus.GaugeValue, float64(m.unreachableInstances), "unreachable")
	values := []float64{
		float64(m.onlineDevices), float64(m.offlineDevices), float64(m.outOfSyncItems), float64(m.outOfSyncBytes),
		m.sendRate, m.receiveRate, float64(m.foldersWithErrors), float64(m.devicesWithErrors),
		float64(m.staleSnapshots), m.maxSnapshotAge,
	}
	for i, value := range values {
		ch <- prometheus.MustNewConstMetric(c.descs[i+1], prometheus.GaugeValue, value)
	}
}

func InitCollector() {
	prometheus.MustRegister(newSyncthingPrometheusCollector(&SyncthingSnapshots))
}
