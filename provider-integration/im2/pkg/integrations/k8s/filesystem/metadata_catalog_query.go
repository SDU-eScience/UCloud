package filesystem

import (
	"bytes"
	"errors"
	"fmt"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/cockroachdb/pebble/v2"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
)

type MetadataDirectoryStats struct {
	Path                    string
	LogicalBytes            uint64
	AllocatedBytes          uint64
	FileCount               uint64
	DirectoryCount          uint64
	ObservedAtUnixNano      int64
	AggregateObservedAtNano int64
	EntryGeneration         uint64
	AggregateGeneration     uint64
	CompleteCoverage        bool
}

type MetadataSearchResult struct {
	Path  string
	Entry MetadataEntry
}

type MetadataSearchResponse struct {
	Results  []MetadataSearchResult
	Complete bool
}

type MetadataCatalogMetrics struct {
	DriveID                   string
	ScansSubmitted            uint64
	ScansInProgress           uint64
	ScansCompleted            uint64
	ScansFailed               uint64
	NameRefreshesInProgress   uint64
	NameRefreshesFailed       uint64
	LastNameRefreshStatus     string
	TotalFilesScanned         uint64
	TotalDirectoriesScanned   uint64
	TotalLogicalBytesScanned  uint64
	LastFilesScanned          uint64
	LastDirectoriesScanned    uint64
	LastLogicalBytesScanned   uint64
	LastAllocatedBytesScanned uint64
	LastScanDurationNanos     int64
	LastScanCompletedAtNano   int64
	LastScanStatus            string
	LastScanEntriesPerSecond  float64
	QueriesCompleted          uint64
	QueriesFailed             uint64
	TotalQueryDurationNanos   int64
	DatabaseBytes             uint64
	DatabasePathBytes         uint64
	DatabaseNameBytes         uint64
}

var (
	metadataMetricScans = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "scans_total",
		Help:      "Total number of metadata catalog scan events",
	}, []string{"status"})
	metadataMetricScanDuration = promauto.NewSummary(prometheus.SummaryOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "scan_duration_seconds",
		Help:      "Summary of metadata catalog scan durations in seconds",
	})
	metadataMetricScansInProgress = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "scans_in_progress",
		Help:      "Number of metadata catalog scans currently in progress",
	})
	metadataMetricScanRate = promauto.NewSummary(prometheus.SummaryOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "scan_entries_per_second",
		Help:      "Summary of entries processed per second by successful metadata catalog scans",
	})
	metadataMetricEntriesScanned = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "entries_scanned_total",
		Help:      "Total number of entries collected by metadata catalog scans",
	}, []string{"type"})
	metadataMetricLogicalBytesScanned = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "logical_bytes_scanned_total",
		Help:      "Total logical bytes observed by metadata catalog scans",
	})
	metadataMetricDatabaseBytes = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "database_bytes",
		Help:      "Pebble disk space used by metadata catalogs",
	}, []string{"drive_id"})
	metadataMetricDatabaseKeyspaceBytes = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "database_keyspace_bytes",
		Help:      "Estimated Pebble disk space used by metadata catalog keyspaces",
	}, []string{"drive_id", "keyspace"})
	metadataMetricQueries = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "queries_total",
		Help:      "Total number of metadata catalog queries",
	}, []string{"operation", "status"})
	metadataMetricQueryDuration = promauto.NewSummaryVec(prometheus.SummaryOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "query_duration_seconds",
		Help:      "Summary of metadata catalog query durations in seconds",
	}, []string{"operation"})
	metadataMetricNameRefreshes = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "name_refreshes_total",
		Help:      "Total number of metadata catalog NAME refreshes",
	}, []string{"status"})
	metadataMetricNameRefreshesInProgress = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "metadata_catalog",
		Name:      "name_refreshes_in_progress",
		Help:      "Number of metadata catalog NAME refreshes currently in progress",
	})
)

var metadataCollectedMetrics = struct {
	sync.Mutex
	drives map[string]MetadataCatalogMetrics
}{drives: map[string]MetadataCatalogMetrics{}}

var errMetadataCatalogNotFound = errors.New("metadata catalog has not been created")

func MetadataLookupDirectoryStats(ucloudPath string) (result MetadataDirectoryStats, found bool, err error) {
	startedAt := time.Now()
	driveID := ""
	defer func() { metadataRecordQuery(driveID, "directory_stats", time.Since(startedAt), err) }()

	internalPath, ok, drive := UCloudToInternal(ucloudPath)
	if !ok {
		return result, false, fmt.Errorf("unknown UCloud path %q", ucloudPath)
	}
	driveID = drive.Id
	driveRoot, ok, _ := DriveToLocalPath(drive)
	if !ok {
		return result, false, fmt.Errorf("drive %q is not available on this provider", drive.Id)
	}
	components, err := metadataComponentsBelowDrive(internalPath, driveRoot)
	if err != nil {
		return result, false, err
	}

	db, closeDB, err := metadataOpenDatabaseForQuery(drive.Id)
	if err != nil {
		if errors.Is(err, errMetadataCatalogNotFound) {
			return result, false, nil
		}
		return result, false, err
	}
	defer closeDB()
	entry, found, err := metadataReadEntry(db, metadataPathKey(components))
	if err != nil || !found {
		return result, found, err
	}
	if entry.EntryType != MetaEntryDirectory {
		return result, false, fmt.Errorf("%q is not a directory", ucloudPath)
	}
	_, rootCloser, rootErr := db.Get(metadataPathKey(nil))
	complete := rootErr == nil
	if rootCloser != nil {
		_ = rootCloser.Close()
	}
	if rootErr != nil && !errors.Is(rootErr, pebble.ErrNotFound) {
		return result, false, rootErr
	}
	_, pendingCloser, pendingErr := db.Get(metadataPendingAggregateKey)
	if pendingCloser != nil {
		_ = pendingCloser.Close()
	}
	if pendingErr == nil {
		complete = false
	} else if !errors.Is(pendingErr, pebble.ErrNotFound) {
		return result, false, pendingErr
	}
	return MetadataDirectoryStats{
		Path:                    ucloudPath,
		LogicalBytes:            entry.RecursiveLogicalBytes,
		AllocatedBytes:          entry.RecursiveAllocatedSize.GetOrDefault(0),
		FileCount:               entry.RecursiveFileCount,
		DirectoryCount:          entry.RecursiveDirectoryCount,
		ObservedAtUnixNano:      entry.ObservedAtUnixNano,
		AggregateObservedAtNano: entry.AggregateObservedAt,
		EntryGeneration:         entry.EntryGeneration,
		AggregateGeneration:     entry.AggregateGeneration,
		CompleteCoverage:        complete,
	}, true, nil
}

func MetadataSearchByNamePrefix(driveID, prefix string, limit int) (response MetadataSearchResponse, err error) {
	startedAt := time.Now()
	defer func() { metadataRecordQuery(driveID, "name_prefix", time.Since(startedAt), err) }()
	if limit <= 0 {
		return response, errors.New("search limit must be positive")
	}
	if _, ok := ResolveDrive(driveID); !ok {
		return response, fmt.Errorf("unknown drive %q", driveID)
	}

	db, closeDB, err := metadataOpenDatabaseForQuery(driveID)
	if err != nil {
		if errors.Is(err, errMetadataCatalogNotFound) {
			return MetadataSearchResponse{Results: []MetadataSearchResult{}, Complete: false}, nil
		}
		return response, err
	}
	defer closeDB()
	pendingBefore, err := metadataNameRefreshPending(db)
	if err != nil {
		return response, err
	}
	response.Results, err = metadataSearchByNamePrefixInDB(db, driveID, prefix, limit)
	if err != nil {
		return response, err
	}
	pendingAfter, err := metadataNameRefreshPending(db)
	if err != nil {
		return response, err
	}
	response.Complete = !pendingBefore && !pendingAfter
	return response, nil
}

func metadataNameRefreshPending(db *pebble.DB) (bool, error) {
	_, closer, err := db.Get(metadataPendingNameKey)
	if closer != nil {
		_ = closer.Close()
	}
	if errors.Is(err, pebble.ErrNotFound) {
		return false, nil
	}
	return err == nil, err
}

func metadataSearchByNamePrefixInDB(db *pebble.DB, driveID, prefix string, limit int) (results []MetadataSearchResult, err error) {
	if limit <= 0 {
		return nil, errors.New("search limit must be positive")
	}
	normalizedPrefix := metadataNormalizedName([]byte(prefix))
	lower := append([]byte{MetaKeyspaceName}, normalizedPrefix...)
	upper := metadataPrefixSuccessor(lower)
	iter, err := db.NewIter(&pebble.IterOptions{LowerBound: lower, UpperBound: upper})
	if err != nil {
		return nil, err
	}
	defer iter.Close()
	for valid := iter.First(); valid && len(results) < limit; valid = iter.Next() {
		pathKey, keyErr := metadataPathKeyFromNameKey(iter.Key())
		if keyErr != nil {
			return nil, keyErr
		}
		entry, present, readErr := metadataReadEntry(db, pathKey)
		if readErr != nil {
			return nil, readErr
		}
		if !present {
			continue
		}
		components, keyErr := metadataPathComponents(pathKey)
		if keyErr != nil {
			return nil, keyErr
		}
		path := "/" + driveID
		if len(components) > 0 {
			path += "/" + strings.Join(components, "/")
		}
		results = append(results, MetadataSearchResult{Path: path, Entry: entry})
	}
	if err = iter.Error(); err != nil {
		return nil, err
	}
	return results, nil
}

func MetadataCatalogMetricsForDrive(driveID string) (result MetadataCatalogMetrics, err error) {
	startedAt := time.Now()
	defer func() { metadataRecordQuery(driveID, "metrics", time.Since(startedAt), err) }()
	if _, ok := ResolveDrive(driveID); !ok {
		return result, fmt.Errorf("unknown drive %q", driveID)
	}
	db, closeDB, err := metadataOpenDatabaseForQuery(driveID)
	if err != nil {
		if errors.Is(err, errMetadataCatalogNotFound) {
			metadataCollectedMetrics.Lock()
			result = metadataCollectedMetrics.drives[driveID]
			metadataCollectedMetrics.Unlock()
			result.DriveID = driveID
			return result, nil
		}
		return result, err
	}
	databaseBytes := db.Metrics().DiskSpaceUsage()
	pathBytes, pathErr := db.EstimateDiskUsage([]byte{MetaKeyspacePath}, []byte{MetaKeyspaceName})
	nameBytes, nameErr := db.EstimateDiskUsage([]byte{MetaKeyspaceName}, []byte{MetaKeyspaceName + 1})
	closeDB()
	if pathErr != nil {
		return result, pathErr
	}
	if nameErr != nil {
		return result, nameErr
	}
	metadataRecordDatabaseSize(driveID, databaseBytes, pathBytes, nameBytes)
	metadataCollectedMetrics.Lock()
	result = metadataCollectedMetrics.drives[driveID]
	metadataCollectedMetrics.Unlock()
	result.DriveID = driveID
	return result, nil
}

func metadataComponentsBelowDrive(internalPath, driveRoot string) ([]string, error) {
	relative, err := filepath.Rel(filepath.Clean(driveRoot), filepath.Clean(internalPath))
	if err != nil || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
		return nil, fmt.Errorf("path %q is outside drive root", internalPath)
	}
	return metadataRelativeComponents(relative), nil
}

func metadataPathKeyFromNameKey(key []byte) ([]byte, error) {
	if len(key) < 3 || key[0] != MetaKeyspaceName {
		return nil, errors.New("invalid NAME key")
	}
	separator := bytes.IndexByte(key[1:], 0)
	if separator < 0 {
		return nil, errors.New("unterminated NAME key")
	}
	pathSuffix := key[separator+2:]
	if len(pathSuffix) == 0 {
		return nil, errors.New("NAME key does not contain a path")
	}
	return append([]byte{MetaKeyspacePath}, pathSuffix...), nil
}

func metadataOpenDatabaseForQuery(driveID string) (*pebble.DB, func(), error) {
	databasePath := metadataDatabasePath(driveID)
	return metadataAcquireDatabase(databasePath, false)
}

func metadataRecordScanSubmitted(driveID string) {
	metadataMetricScans.WithLabelValues("submitted").Inc()
	metadataCollectedMetrics.Lock()
	metrics := metadataCollectedMetrics.drives[driveID]
	metrics.DriveID = driveID
	metrics.ScansSubmitted++
	metadataCollectedMetrics.drives[driveID] = metrics
	metadataCollectedMetrics.Unlock()
}

func metadataRecordScanStarted(driveID string) {
	metadataMetricScansInProgress.Inc()
	metadataCollectedMetrics.Lock()
	metrics := metadataCollectedMetrics.drives[driveID]
	metrics.DriveID = driveID
	metrics.ScansInProgress++
	metrics.LastScanStatus = "running"
	metadataCollectedMetrics.drives[driveID] = metrics
	metadataCollectedMetrics.Unlock()
}

func metadataRecordScanFinished(driveID string, duration time.Duration, scanErr error) {
	status := "completed"
	metadataMetricScansInProgress.Dec()
	metadataCollectedMetrics.Lock()
	metrics := metadataCollectedMetrics.drives[driveID]
	metrics.DriveID = driveID
	if metrics.ScansInProgress > 0 {
		metrics.ScansInProgress--
	}
	metrics.LastScanDurationNanos = duration.Nanoseconds()
	if scanErr == nil {
		metrics.ScansCompleted++
		metrics.LastScanCompletedAtNano = time.Now().UnixNano()
		metrics.LastScanStatus = "completed"
		if duration > 0 {
			metrics.LastScanEntriesPerSecond = float64(metrics.LastFilesScanned+metrics.LastDirectoriesScanned) / duration.Seconds()
			metadataMetricScanRate.Observe(metrics.LastScanEntriesPerSecond)
		}
	} else {
		status = "failed"
		metrics.ScansFailed++
		metrics.LastScanStatus = "failed"
	}
	metadataCollectedMetrics.drives[driveID] = metrics
	metadataCollectedMetrics.Unlock()
	metadataMetricScans.WithLabelValues(status).Inc()
	metadataMetricScanDuration.Observe(duration.Seconds())
}

func metadataRecordScanContents(driveID string, root MetadataEntry, databaseBytes, pathBytes, nameBytes uint64) {
	metadataCollectedMetrics.Lock()
	metrics := metadataCollectedMetrics.drives[driveID]
	metrics.DriveID = driveID
	metrics.TotalFilesScanned += root.RecursiveFileCount
	metrics.TotalDirectoriesScanned += root.RecursiveDirectoryCount + 1
	metrics.TotalLogicalBytesScanned += root.RecursiveLogicalBytes + root.LogicalSize
	metrics.LastFilesScanned = root.RecursiveFileCount
	metrics.LastDirectoriesScanned = root.RecursiveDirectoryCount + 1
	metrics.LastLogicalBytesScanned = root.RecursiveLogicalBytes + root.LogicalSize
	metrics.LastAllocatedBytesScanned = root.RecursiveAllocatedSize.GetOrDefault(0) + root.AllocatedSize.GetOrDefault(0)
	metrics.DatabaseBytes = databaseBytes
	metrics.DatabasePathBytes = pathBytes
	metrics.DatabaseNameBytes = nameBytes
	metadataCollectedMetrics.drives[driveID] = metrics
	metadataCollectedMetrics.Unlock()
	metadataMetricEntriesScanned.WithLabelValues("file").Add(float64(root.RecursiveFileCount))
	metadataMetricEntriesScanned.WithLabelValues("directory").Add(float64(root.RecursiveDirectoryCount + 1))
	metadataMetricLogicalBytesScanned.Add(float64(root.RecursiveLogicalBytes + root.LogicalSize))
	metadataMetricDatabaseBytes.WithLabelValues(driveID).Set(float64(databaseBytes))
	metadataMetricDatabaseKeyspaceBytes.WithLabelValues(driveID, "path").Set(float64(pathBytes))
	metadataMetricDatabaseKeyspaceBytes.WithLabelValues(driveID, "name").Set(float64(nameBytes))
}

func metadataRecordDatabaseSize(driveID string, databaseBytes, pathBytes, nameBytes uint64) {
	metadataCollectedMetrics.Lock()
	metrics := metadataCollectedMetrics.drives[driveID]
	metrics.DriveID = driveID
	metrics.DatabaseBytes = databaseBytes
	metrics.DatabasePathBytes = pathBytes
	metrics.DatabaseNameBytes = nameBytes
	metadataCollectedMetrics.drives[driveID] = metrics
	metadataCollectedMetrics.Unlock()
	metadataMetricDatabaseBytes.WithLabelValues(driveID).Set(float64(databaseBytes))
	metadataMetricDatabaseKeyspaceBytes.WithLabelValues(driveID, "path").Set(float64(pathBytes))
	metadataMetricDatabaseKeyspaceBytes.WithLabelValues(driveID, "name").Set(float64(nameBytes))
}

func metadataRecordQuery(driveID, operation string, duration time.Duration, queryErr error) {
	status := "completed"
	metadataCollectedMetrics.Lock()
	metrics := metadataCollectedMetrics.drives[driveID]
	metrics.DriveID = driveID
	metrics.TotalQueryDurationNanos += duration.Nanoseconds()
	if queryErr == nil {
		metrics.QueriesCompleted++
	} else {
		status = "failed"
		metrics.QueriesFailed++
	}
	metadataCollectedMetrics.drives[driveID] = metrics
	metadataCollectedMetrics.Unlock()
	metadataMetricQueries.WithLabelValues(operation, status).Inc()
	metadataMetricQueryDuration.WithLabelValues(operation).Observe(duration.Seconds())
}

func metadataRecordNameRefreshStarted(driveID string) {
	metadataMetricNameRefreshesInProgress.Inc()
	metadataCollectedMetrics.Lock()
	metrics := metadataCollectedMetrics.drives[driveID]
	metrics.DriveID = driveID
	metrics.NameRefreshesInProgress++
	metrics.LastNameRefreshStatus = "running"
	metadataCollectedMetrics.drives[driveID] = metrics
	metadataCollectedMetrics.Unlock()
}

func metadataRecordNameRefreshFinished(driveID string, refreshErr error) {
	status := "completed"
	metadataMetricNameRefreshesInProgress.Dec()
	metadataCollectedMetrics.Lock()
	metrics := metadataCollectedMetrics.drives[driveID]
	metrics.DriveID = driveID
	if metrics.NameRefreshesInProgress > 0 {
		metrics.NameRefreshesInProgress--
	}
	if refreshErr == nil {
		metrics.LastNameRefreshStatus = "completed"
	} else {
		status = "failed"
		metrics.NameRefreshesFailed++
		metrics.LastNameRefreshStatus = "failed"
	}
	metadataCollectedMetrics.drives[driveID] = metrics
	metadataCollectedMetrics.Unlock()
	metadataMetricNameRefreshes.WithLabelValues(status).Inc()
}
