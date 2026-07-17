package filesystem

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"math"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/cockroachdb/pebble/v2"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	orc "ucloud.dk/shared/pkg/orchestrators"
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

func metadataLookupRecursiveSizes(drive *orc.Drive, internalPaths []string) (result map[string]int64, err error) {
	result = map[string]int64{}
	if len(internalPaths) == 0 {
		return result, nil
	}
	startedAt := time.Now()
	defer func() { metadataRecordQuery(drive.Id, "recursive_sizes", time.Since(startedAt), err) }()

	driveRoot, ok, _ := DriveToLocalPath(drive)
	if !ok {
		return result, fmt.Errorf("drive %q is not available on this provider", drive.Id)
	}
	db, closeDB, err := metadataOpenDatabaseForQuery(drive.Id)
	if err != nil {
		if errors.Is(err, errMetadataCatalogNotFound) {
			return result, nil
		}
		return result, err
	}
	defer closeDB()

	for _, internalPath := range internalPaths {
		components, componentErr := metadataComponentsBelowDrive(internalPath, driveRoot)
		if componentErr != nil {
			return result, componentErr
		}
		entry, found, readErr := metadataReadEntry(db, metadataPathKey(components))
		if readErr != nil {
			return result, readErr
		}
		if found && entry.EntryType == MetaEntryDirectory && entry.RecursiveLogicalBytes <= math.MaxInt64 {
			result[internalPath] = int64(entry.RecursiveLogicalBytes)
		}
	}
	return result, nil
}

// MetadataSearchByNamePrefix streams name matches below a UCloud folder, with prefix matches first. Substring matching
// requires at least three normalized characters. A drive root is /<drive ID>.
func MetadataSearchByNamePrefix(ctx context.Context, folder, prefix string, limit int, emit func(MetadataSearchResult) bool) (complete bool, err error) {
	startedAt := time.Now()
	driveID := ""
	defer func() { metadataRecordQuery(driveID, "name_prefix", time.Since(startedAt), err) }()
	if limit <= 0 {
		return false, errors.New("search limit must be positive")
	}
	internalPath, ok, drive := UCloudToInternal(folder)
	if !ok {
		return false, fmt.Errorf("unknown UCloud path %q", folder)
	}
	driveID = drive.Id
	driveRoot, ok, _ := DriveToLocalPath(drive)
	if !ok {
		return false, fmt.Errorf("drive %q is not available on this provider", drive.Id)
	}
	folderComponents, err := metadataComponentsBelowDrive(internalPath, driveRoot)
	if err != nil {
		return false, err
	}

	db, closeDB, err := metadataOpenDatabaseForQuery(driveID)
	if err != nil {
		if errors.Is(err, errMetadataCatalogNotFound) {
			return false, nil
		}
		return false, err
	}
	defer closeDB()
	pendingBefore, err := metadataNameRefreshPending(db)
	if err != nil {
		return false, err
	}
	if err = metadataSearchByNamePrefixInDB(ctx, db, driveID, folderComponents, prefix, limit, emit); err != nil {
		return false, err
	}
	pendingAfter, err := metadataNameRefreshPending(db)
	if err != nil {
		return false, err
	}
	return !pendingBefore && !pendingAfter, nil
}

func metadataNameRefreshPending(db *pebble.DB) (bool, error) {
	_, closer, err := db.Get(metadataPendingNameKey)
	if closer != nil {
		_ = closer.Close()
	}
	if errors.Is(err, pebble.ErrNotFound) {
		current, versionErr := metadataSearchIndexCurrent(db)
		return !current, versionErr
	}
	return err == nil, err
}

func metadataSearchIndexCurrent(db *pebble.DB) (bool, error) {
	value, closer, err := db.Get(metadataSearchIndexVersionKey)
	if errors.Is(err, pebble.ErrNotFound) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	defer closer.Close()
	return bytes.Equal(value, metadataSearchIndexVersion), nil
}

func metadataSearchByNamePrefixInDB(ctx context.Context, db *pebble.DB, driveID string, folderComponents []string, prefix string, limit int, emit func(MetadataSearchResult) bool) (err error) {
	if limit <= 0 {
		return errors.New("search limit must be positive")
	}
	normalizedQuery := metadataNormalizedName([]byte(prefix))
	emitted := 0
	stopped := false
	searchIndex := func(keyspace byte, indexPrefix []byte, matches func([]byte) bool) error {
		lower := append([]byte{keyspace}, indexPrefix...)
		iter, iterErr := db.NewIter(&pebble.IterOptions{LowerBound: lower, UpperBound: metadataPrefixSuccessor(lower)})
		if iterErr != nil {
			return iterErr
		}
		defer iter.Close()
		for valid := iter.First(); valid && emitted < limit && !stopped; valid = iter.Next() {
			select {
			case <-ctx.Done():
				return ctx.Err()
			default:
			}
			pathKey, keyErr := metadataPathKeyFromSearchIndexKey(iter.Key(), keyspace)
			if keyErr != nil {
				return keyErr
			}
			entry, present, readErr := metadataReadEntry(db, pathKey)
			if readErr != nil {
				return readErr
			}
			if !present {
				continue
			}
			components, keyErr := metadataPathComponents(pathKey)
			if keyErr != nil {
				return keyErr
			}
			if len(components) == 0 || !metadataComponentsContain(folderComponents, components) {
				continue
			}
			normalizedName := metadataNormalizedName([]byte(components[len(components)-1]))
			if !matches(normalizedName) {
				continue
			}
			emitted++
			path := "/" + driveID + "/" + strings.Join(components, "/")
			stopped = !emit(MetadataSearchResult{Path: path, Entry: entry})
		}
		return iter.Error()
	}

	if err = searchIndex(MetaKeyspaceName, normalizedQuery, func(name []byte) bool {
		return bytes.HasPrefix(name, normalizedQuery)
	}); err != nil || emitted >= limit || stopped {
		return err
	}
	trigrams := metadataNameTrigrams(normalizedQuery)
	if len(trigrams) == 0 {
		return nil
	}
	trigramPrefix := append(append([]byte(nil), trigrams[0]...), 0)
	trigramBytes, err := db.EstimateDiskUsage(append([]byte{MetaKeyspaceTrigram}, trigramPrefix...), metadataPrefixSuccessor(append([]byte{MetaKeyspaceTrigram}, trigramPrefix...)))
	if err != nil {
		return err
	}
	for _, trigram := range trigrams[1:] {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}
		candidatePrefix := append(append([]byte(nil), trigram...), 0)
		lower := append([]byte{MetaKeyspaceTrigram}, candidatePrefix...)
		candidateBytes, estimateErr := db.EstimateDiskUsage(lower, metadataPrefixSuccessor(lower))
		if estimateErr != nil {
			return estimateErr
		}
		if candidateBytes < trigramBytes {
			trigramPrefix = candidatePrefix
			trigramBytes = candidateBytes
		}
	}
	return searchIndex(MetaKeyspaceTrigram, trigramPrefix, func(name []byte) bool {
		return !bytes.HasPrefix(name, normalizedQuery) && bytes.Contains(name, normalizedQuery)
	})
}

func metadataComponentsContain(parent, child []string) bool {
	if len(parent) > len(child) {
		return false
	}
	for i := range parent {
		if parent[i] != child[i] {
			return false
		}
	}
	return true
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
	nameBytes, nameErr := db.EstimateDiskUsage([]byte{MetaKeyspaceName}, []byte{MetaKeyspaceTrigram + 1})
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

func metadataPathKeyFromSearchIndexKey(key []byte, keyspace byte) ([]byte, error) {
	if len(key) < 3 || key[0] != keyspace {
		return nil, errors.New("invalid search index key")
	}
	separator := bytes.IndexByte(key[1:], 0)
	if separator < 0 {
		return nil, errors.New("unterminated search index key")
	}
	pathSuffix := key[separator+2:]
	if len(pathSuffix) == 0 {
		return nil, errors.New("search index key does not contain a path")
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
