package filesystem

import (
	"context"
	"errors"
	"io"
	"os"
	"sync"
	"sync/atomic"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"golang.org/x/sys/unix"
	"golang.org/x/time/rate"
)

var metadataMetricLatencyBuckets = prometheus.ExponentialBuckets(0.00001, 4, 13)
var metadataMetricScanBuckets = prometheus.ExponentialBuckets(0.001, 4, 14)

var (
	metadataMetricEntriesVisited      = metadataNewCounterVec("entries_visited_total", "Entries visited during traversal, including unpublished attempts", "type")
	metadataMetricEntriesPublished    = metadataNewCounterVec("entries_published_total", "Entries in successfully ingested PATH replacements, including scans that fail after ingestion", "type")
	metadataMetricSearchKeysGenerated = metadataNewCounterVec("search_keys_generated_total", "Search keys generated during traversal", "keyspace")
	metadataMetricOldestScan          = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "scan_oldest_start_timestamp_seconds",
		Help:      "Start timestamp of the oldest running scan; absent when idle",
	}, nil)
	metadataMetricScansByPhase      = metadataNewGaugeVec("scans_by_phase", "Running scans by exclusive pipeline phase", "phase")
	metadataMetricPhaseDuration     = metadataNewHistogramVec("scan_phase_duration_seconds", "Exclusive scan phase duration, including failed attempts", metadataMetricScanBuckets, "phase")
	metadataMetricBuildDuration     = metadataNewHistogramVec("build_operation_duration_seconds", "Nested build operation duration; included in scan phase time", metadataMetricLatencyBuckets, "operation")
	metadataMetricLookupWorkers     = metadataNewGaugeVec("lookup_workers", "Lookup workers by current state", "state")
	metadataMetricLookupSubmitters  = metadataNewGauge("lookup_submitters", "Submitters blocked waiting for a lookup worker")
	metadataMetricLookupSubmitWait  = metadataNewHistogramVec("lookup_submit_wait_seconds", "Time handing a lookup request to a worker", metadataMetricLatencyBuckets)
	metadataMetricLimiterWait       = metadataNewHistogramVec("limiter_wait_duration_seconds", "Time acquiring scan operation budget, excluding filesystem execution", metadataMetricLatencyBuckets, "operation")
	metadataMetricLimiterOperations = metadataNewCounterVec("limiter_operations_total", "Scan operations admitted by the limiter, not physical disk IOPS", "operation")
	metadataMetricLookupLimit       = metadataNewGauge("lookup_workers_limit", "Configured lookup worker limit")
	metadataMetricScanLimit         = metadataNewGauge("scan_workers_limit", "Configured scan worker limit")
	metadataMetricLimiterLimit      = metadataNewGauge("limiter_operations_per_second_limit", "Configured shared scan operation budget per second")
	metadataMetricFsDuration        = metadataNewHistogramVec("fs_operation_duration_seconds", "Filesystem operation duration, excluding limiter waits", metadataMetricLatencyBuckets, "operation")
	metadataMetricFsOperations      = metadataNewCounterVec("fs_operations_total", "Filesystem operation attempts by bounded result category", "operation", "result")
	metadataMetricStatxFallbacks    = metadataNewCounterVec("statx_fallbacks_total", "Lookups falling back from statx to fstatat", "reason")
	metadataMetricPendingDrives     = metadataNewGauge("scan_pending_drives", "Distinct drives with scan requests awaiting execution")
	metadataMetricQueueWait         = metadataNewHistogramVec("scan_queue_wait_seconds", "Queue wait for executed scans, measured from the earliest coalesced submission", metadataMetricScanBuckets)
	metadataMetricCoalesced         = metadataNewCounterVec("scan_requests_coalesced_total", "Scan requests combined with other queued or active work")
	metadataMetricFailures          = metadataNewCounterVec("scan_failures_total", "Failed scan attempts by pipeline phase and bounded reason", "phase", "reason")
	metadataMetricDiscardedEntries  = metadataNewCounterVec("scan_discarded_entries_total", "Visited entries discarded before PATH publication")
)

type metadataScanMetrics struct {
	startedAt    time.Time
	phaseAt      time.Time
	phase        string
	failurePhase string
	visited      uint64
	published    bool
}

var metadataMetricsState = struct {
	sync.Mutex
	scans   map[*metadataScanMetrics]struct{}
	pending map[string]int
}{
	scans:   map[*metadataScanMetrics]struct{}{},
	pending: map[string]int{},
}

var metadataProgress = struct {
	sync.Once
	files       atomic.Uint64
	directories atomic.Uint64
	names       atomic.Uint64
	trigrams    atomic.Uint64
}{}

func metadataNewCounterVec(name, help string, labels ...string) *prometheus.CounterVec {
	return promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      name,
		Help:      help,
	}, labels)
}

func metadataNewGaugeVec(name, help string, labels ...string) *prometheus.GaugeVec {
	return promauto.NewGaugeVec(prometheus.GaugeOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      name,
		Help:      help,
	}, labels)
}

func metadataNewGauge(name, help string) prometheus.Gauge {
	return promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      name,
		Help:      help,
	})
}

func metadataNewHistogramVec(name, help string, buckets []float64, labels ...string) *prometheus.HistogramVec {
	return promauto.NewHistogramVec(prometheus.HistogramOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      name,
		Help:      help,
		Buckets:   buckets,
	}, labels)
}

func metadataConfigureMetrics(config MetadataCatalogConfig) {
	metadataMetricLookupLimit.Set(float64(config.ParallelLookups))
	metadataMetricScanLimit.Set(float64(config.ParallelScans))
	metadataMetricLimiterLimit.Set(float64(config.IOPS))
	for _, phase := range []string{
		"setup", "traverse", "flush_runs", "merge", "publication_wait", "database_open",
		"recovery", "old_names", "journal", "ingest", "ancestors", "accounting", "name_refresh", "cleanup",
	} {
		metadataMetricScansByPhase.WithLabelValues(phase)
		metadataMetricPhaseDuration.WithLabelValues(phase)
		for _, reason := range []string{
			"cancelled", "permission", "not_found", "stale", "changed", "unsupported", "interrupted",
			"io", "no_space", "timeout", "other", "invalidated", "aggregate_repair", "name_repair", "accounting",
		} {
			metadataMetricFailures.WithLabelValues(phase, reason)
		}
	}
	for _, state := range []string{"idle", "limiter_wait", "filesystem"} {
		metadataMetricLookupWorkers.WithLabelValues(state)
	}
	for _, operation := range []string{"statx", "fstatat", "readdir", "open_directory", "open_root", "fstat", "revalidate"} {
		metadataMetricFsDuration.WithLabelValues(operation)
		for _, result := range []string{
			"success", "eof", "cancelled", "permission", "not_found", "stale", "changed", "unsupported", "interrupted", "io", "no_space", "timeout", "other",
		} {
			metadataMetricFsOperations.WithLabelValues(operation, result)
		}
	}
	for _, operation := range []string{"stat", "readdir", "open_directory", "open_root", "fstat"} {
		metadataMetricLimiterOperations.WithLabelValues(operation)
		metadataMetricLimiterWait.WithLabelValues(operation)
	}
	for _, reason := range []string{"unavailable", "unsupported_syscall", "unsupported_filesystem", "unsupported_flags", "missing_attributes"} {
		metadataMetricStatxFallbacks.WithLabelValues(reason)
	}
	for _, status := range []string{"submitted", "completed", "failed"} {
		metadataMetricScans.WithLabelValues(status)
	}
	metadataMetricLookupSubmitWait.WithLabelValues()
	metadataMetricQueueWait.WithLabelValues()
	metadataMetricBuildDuration.WithLabelValues("name_keys")
	metadataMetricBuildDuration.WithLabelValues("flush_run")
	metadataMetricEntriesVisited.WithLabelValues("file")
	metadataMetricEntriesVisited.WithLabelValues("directory")
	metadataMetricEntriesPublished.WithLabelValues("file")
	metadataMetricEntriesPublished.WithLabelValues("directory")
	metadataMetricEntriesScanned.WithLabelValues("file")
	metadataMetricEntriesScanned.WithLabelValues("directory")
	metadataMetricSearchKeysGenerated.WithLabelValues("name")
	metadataMetricSearchKeysGenerated.WithLabelValues("trigram")
	metadataMetricCoalesced.WithLabelValues()
	metadataMetricDiscardedEntries.WithLabelValues()
}

func metadataStartScanMetrics() *metadataScanMetrics {
	metadataProgress.Do(func() {
		go func() {
			ticker := time.NewTicker(time.Second)
			for range ticker.C {
				metadataFlushProgress()
			}
		}()
	})
	now := time.Now()
	metrics := &metadataScanMetrics{
		startedAt: now,
		phaseAt:   now,
		phase:     "setup",
	}
	metadataMetricScansByPhase.WithLabelValues(metrics.phase).Inc()
	metadataMetricsState.Lock()
	metadataMetricsState.scans[metrics] = struct{}{}
	metadataUpdateOldestScan()
	metadataMetricsState.Unlock()
	return metrics
}

func metadataSetScanPhase(metrics *metadataScanMetrics, phase string) {
	now := time.Now()
	metadataMetricPhaseDuration.WithLabelValues(metrics.phase).Observe(now.Sub(metrics.phaseAt).Seconds())
	metadataMetricScansByPhase.WithLabelValues(metrics.phase).Dec()
	metrics.phase = phase
	metrics.phaseAt = now
	metadataMetricScansByPhase.WithLabelValues(phase).Inc()
}

func metadataFinishScanMetrics(metrics *metadataScanMetrics, scanErr error) {
	metadataMetricPhaseDuration.WithLabelValues(metrics.phase).Observe(time.Since(metrics.phaseAt).Seconds())
	metadataMetricScansByPhase.WithLabelValues(metrics.phase).Dec()
	if scanErr != nil {
		phase := metrics.failurePhase
		if phase == "" {
			phase = metrics.phase
		}
		reason := metadataOperationResult(scanErr)
		if errors.Is(scanErr, errMetadataScanInvalidated) {
			reason = "invalidated"
		} else if errors.Is(scanErr, errMetadataAggregateRepairRequired) {
			reason = "aggregate_repair"
		} else if errors.Is(scanErr, errMetadataNameRepairRequired) {
			reason = "name_repair"
		} else if phase == "accounting" {
			reason = "accounting"
		}
		metadataMetricFailures.WithLabelValues(phase, reason).Inc()
	}
	if !metrics.published {
		metadataMetricDiscardedEntries.WithLabelValues().Add(float64(metrics.visited))
	}
	metadataFlushProgress()
	metadataMetricsState.Lock()
	delete(metadataMetricsState.scans, metrics)
	metadataUpdateOldestScan()
	metadataMetricsState.Unlock()
}

func metadataUpdateOldestScan() {
	var oldest time.Time
	for scan := range metadataMetricsState.scans {
		if oldest.IsZero() || scan.startedAt.Before(oldest) {
			oldest = scan.startedAt
		}
	}
	if oldest.IsZero() {
		metadataMetricOldestScan.DeleteLabelValues()
		return
	}
	metadataMetricOldestScan.WithLabelValues().Set(float64(oldest.UnixNano()) / float64(time.Second))
}

func metadataFlushProgress() {
	metadataMetricEntriesVisited.WithLabelValues("file").Add(float64(metadataProgress.files.Swap(0)))
	metadataMetricEntriesVisited.WithLabelValues("directory").Add(float64(metadataProgress.directories.Swap(0)))
	metadataMetricSearchKeysGenerated.WithLabelValues("name").Add(float64(metadataProgress.names.Swap(0)))
	metadataMetricSearchKeysGenerated.WithLabelValues("trigram").Add(float64(metadataProgress.trigrams.Swap(0)))
}

func metadataRecordVisited(metrics *metadataScanMetrics, directory bool) {
	metrics.visited++
	if directory {
		metadataProgress.directories.Add(1)
	} else {
		metadataProgress.files.Add(1)
	}
}

func metadataQueueChange(driveId string, delta int) {
	metadataMetricsState.Lock()
	count := metadataMetricsState.pending[driveId] + delta
	if count == 0 {
		delete(metadataMetricsState.pending, driveId)
	} else {
		metadataMetricsState.pending[driveId] = count
	}
	metadataMetricPendingDrives.Set(float64(len(metadataMetricsState.pending)))
	metadataMetricsState.Unlock()
}

func metadataWaitForScanOperation(ctx context.Context, limiter *rate.Limiter, operation string) error {
	startedAt := time.Now()
	err := limiter.Wait(ctx)
	metadataMetricLimiterWait.WithLabelValues(operation).Observe(time.Since(startedAt).Seconds())
	if err == nil {
		metadataMetricLimiterOperations.WithLabelValues(operation).Inc()
	}
	return err
}

func metadataRecordFsOperation(operation string, startedAt time.Time, err error) {
	metadataMetricFsDuration.WithLabelValues(operation).Observe(time.Since(startedAt).Seconds())
	metadataMetricFsOperations.WithLabelValues(operation, metadataOperationResult(err)).Inc()
}

func metadataOperationResult(err error) string {
	switch {
	case err == nil:
		return "success"
	case errors.Is(err, io.EOF):
		return "eof"
	case errors.Is(err, context.Canceled), errors.Is(err, context.DeadlineExceeded):
		return "cancelled"
	case errors.Is(err, os.ErrPermission), errors.Is(err, unix.EPERM):
		return "permission"
	case errors.Is(err, os.ErrNotExist):
		return "not_found"
	case errors.Is(err, unix.ESTALE):
		return "stale"
	case errors.Is(err, unix.ENOTDIR), errors.Is(err, unix.ELOOP):
		return "changed"
	case errors.Is(err, unix.ENOSYS), errors.Is(err, unix.EOPNOTSUPP), errors.Is(err, unix.EINVAL):
		return "unsupported"
	case errors.Is(err, unix.EINTR):
		return "interrupted"
	case errors.Is(err, unix.EIO):
		return "io"
	case errors.Is(err, unix.ENOSPC), errors.Is(err, unix.EDQUOT):
		return "no_space"
	case errors.Is(err, unix.ETIMEDOUT):
		return "timeout"
	default:
		return "other"
	}
}
