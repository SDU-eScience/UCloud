package filesystem

import (
	"bytes"
	"container/heap"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/cockroachdb/pebble/v2"
	"ucloud.dk/pkg/integrations/k8s/shared"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const (
	filesVisualizationMaxDuration = 100 * time.Millisecond
	filesVisualizationMaxEntries  = 1000
	filesVisualizationIndexWait   = 3 * time.Second
)

type filesVisualizationCandidate struct {
	path  string
	entry MetadataEntry
}

type filesVisualizationQueue []filesVisualizationCandidate

func (q filesVisualizationQueue) Len() int { return len(q) }
func (q filesVisualizationQueue) Less(i, j int) bool {
	iSize := filesVisualizationEntrySize(q[i].entry)
	jSize := filesVisualizationEntrySize(q[j].entry)
	if iSize == jSize {
		return q[i].path > q[j].path
	}
	return iSize < jSize
}
func (q filesVisualizationQueue) Swap(i, j int) { q[i], q[j] = q[j], q[i] }
func (q *filesVisualizationQueue) Push(value any) {
	*q = append(*q, value.(filesVisualizationCandidate))
}
func (q *filesVisualizationQueue) Pop() any {
	old := *q
	last := len(old) - 1
	result := old[last]
	*q = old[:last]
	return result
}

func visualize(request orc.FilesProviderVisualizeRequest) (orc.FilesVisualizeResponse, *util.HttpError) {
	config := shared.ServiceConfig.FileSystem.MetadataCatalog
	if !config.Enabled || !config.EnableIntegration {
		return orc.FilesVisualizeResponse{}, util.HttpErr(http.StatusNotFound, "storage visualization is not supported")
	}

	_, ok, drive := UCloudToInternal(request.Path)
	if !ok {
		return orc.FilesVisualizeResponse{}, util.UserHttpError("unknown storage path")
	}
	driveRoot := "/" + drive.Id
	stats, indexed, err := MetadataLookupDirectoryStats(driveRoot)
	if err != nil {
		return orc.FilesVisualizeResponse{}, util.ServerHttpError("failed to query storage metadata")
	}
	if !indexed || !stats.CompleteCoverage {
		initialization := metadataEnsureIndex(drive.Id)
		select {
		case <-initialization.done:
			if initialization.err != nil {
				return orc.FilesVisualizeResponse{}, util.ServerHttpError("failed to build storage metadata index")
			}
		case <-time.After(filesVisualizationIndexWait):
		}

		stats, indexed, err = MetadataLookupDirectoryStats(driveRoot)
		if err != nil {
			return orc.FilesVisualizeResponse{}, util.ServerHttpError("failed to query storage metadata")
		}
		if !indexed || !stats.CompleteCoverage {
			return orc.FilesVisualizeResponse{Entries: []orc.FilesVisualizeEntry{}, Indexing: util.OptValue(true)}, nil
		}
	}

	entries, observedAt, fileCount, directoryCount, complete, found, err := metadataVisualize(request.Path, filesVisualizationMaxEntries, filesVisualizationMaxDuration)
	if errors.Is(err, errMetadataIndexIncomplete) {
		metadataEnsureIndex(drive.Id)
		return orc.FilesVisualizeResponse{Entries: []orc.FilesVisualizeEntry{}, Indexing: util.OptValue(true)}, nil
	}
	if err != nil {
		return orc.FilesVisualizeResponse{}, util.ServerHttpError("failed to query storage metadata")
	}
	if !found {
		return orc.FilesVisualizeResponse{}, util.HttpErr(http.StatusNotFound, "path is not present in the metadata catalog")
	}
	return orc.FilesVisualizeResponse{
		Entries:        entries,
		LastUpdatedAt:  util.OptValue(fnd.Timestamp(time.Unix(0, observedAt))),
		FileCount:      fileCount,
		DirectoryCount: directoryCount,
		Complete:       complete,
		Indexing:       util.OptValue(false),
	}, nil
}

func metadataVisualize(ucloudPath string, limit int, maxDuration time.Duration) (result []orc.FilesVisualizeEntry, observedAt int64, fileCount, directoryCount uint64, complete, found bool, err error) {
	internalPath, ok, drive := UCloudToInternal(ucloudPath)
	if !ok {
		return nil, 0, 0, 0, false, false, fmt.Errorf("unknown UCloud path %q", ucloudPath)
	}
	driveRoot, ok, _ := DriveToLocalPath(drive)
	if !ok {
		return nil, 0, 0, 0, false, false, fmt.Errorf("drive %q is not available on this provider", drive.Id)
	}
	rootComponents, err := metadataComponentsBelowDrive(internalPath, driveRoot)
	if err != nil {
		return nil, 0, 0, 0, false, false, err
	}
	visibleDrive, _, ok := ResolveDriveByUCloudPath(ucloudPath)
	if !ok {
		return nil, 0, 0, 0, false, false, fmt.Errorf("unknown UCloud path %q", ucloudPath)
	}
	visibleRoot, ok, _ := DriveToLocalPath(visibleDrive)
	if !ok || !metadataPathContains(visibleRoot, internalPath) {
		return nil, 0, 0, 0, false, false, errors.New("visualization path is outside the requested drive")
	}
	visibleComponents, err := metadataComponentsBelowDrive(visibleRoot, driveRoot)
	if err != nil {
		return nil, 0, 0, 0, false, false, err
	}
	db, closeDB, err := metadataOpenDatabaseForQuery(drive.Id)
	if err != nil {
		if errors.Is(err, errMetadataCatalogNotFound) {
			return []orc.FilesVisualizeEntry{}, 0, 0, 0, false, false, nil
		}
		return nil, 0, 0, 0, false, false, err
	}
	defer closeDB()
	snapshot := metadataNewQuerySnapshot(db)
	defer snapshot.Close()
	aggregatesComplete, err := metadataAggregatesComplete(snapshot)
	if err != nil {
		return nil, 0, 0, 0, false, false, err
	}
	if !aggregatesComplete {
		return nil, 0, 0, 0, false, false, errMetadataIndexIncomplete
	}
	result, observedAt, fileCount, directoryCount, complete, found, err = metadataVisualizeInReader(snapshot, drive.Id, rootComponents, limit, maxDuration)
	for i := range result {
		components := util.Components(result[i].Path)[1:]
		path, ok := metadataVisiblePath(visibleDrive.Id, visibleComponents, components)
		if !ok {
			return nil, 0, 0, 0, false, false, errors.New("visualization result is outside the requested drive")
		}
		result[i].Path = path
	}
	return result, observedAt, fileCount, directoryCount, complete, found, err
}

func metadataVisualizeInDB(db *pebble.DB, driveID string, rootComponents []string, limit int, maxDuration time.Duration) (result []orc.FilesVisualizeEntry, observedAt int64, fileCount, directoryCount uint64, complete, found bool, err error) {
	return metadataVisualizeInReader(db, driveID, rootComponents, limit, maxDuration)
}

func metadataVisualizeInReader(reader pebble.Reader, driveID string, rootComponents []string, limit int, maxDuration time.Duration) (result []orc.FilesVisualizeEntry, observedAt int64, fileCount, directoryCount uint64, complete, found bool, err error) {
	if limit <= 0 {
		return nil, 0, 0, 0, false, false, errors.New("visualization limit must be positive")
	}
	rootEntry, found, err := metadataReadEntry(reader, metadataPathKey(rootComponents))
	if err != nil || !found {
		return nil, 0, 0, 0, false, found, err
	}
	startedAt := time.Now()
	deadline := startedAt.Add(maxDuration)
	rootPath := "/" + driveID
	if len(rootComponents) > 0 {
		rootPath += "/" + strings.Join(rootComponents, "/")
	}
	result = append(result, filesVisualizationApiEntry(rootPath, rootEntry))
	observedAt = rootEntry.ObservedAtUnixNano
	if rootEntry.EntryType == MetaEntryDirectory {
		observedAt = rootEntry.AggregateObservedAt
		fileCount = rootEntry.RecursiveFileCount
		directoryCount = rootEntry.RecursiveDirectoryCount
	} else {
		fileCount = 1
	}

	queue := &filesVisualizationQueue{}
	heap.Init(queue)
	childrenComplete, listErr := metadataVisualizationChildren(reader, driveID, rootComponents, deadline, queue, limit-1)
	if listErr != nil {
		return nil, 0, 0, 0, false, true, listErr
	}
	complete = childrenComplete

	for queue.Len() > 0 && len(result) < limit && time.Now().Before(deadline) {
		largest := 0
		for i := 1; i < queue.Len(); i++ {
			if queue.Less(largest, i) {
				largest = i
			}
		}
		candidate := heap.Remove(queue, largest).(filesVisualizationCandidate)
		result = append(result, filesVisualizationApiEntry(candidate.path, candidate.entry))
		if candidate.entry.EntryType != MetaEntryDirectory {
			continue
		}

		components := util.Components(candidate.path)[1:]
		listedAll, listErr := metadataVisualizationChildren(reader, driveID, components, deadline, queue, limit-len(result))
		if listErr != nil {
			return nil, 0, 0, 0, false, true, listErr
		}
		complete = complete && listedAll
	}
	complete = complete && queue.Len() == 0
	return result, observedAt, fileCount, directoryCount, complete, true, nil
}

func metadataVisualizationChildren(reader pebble.Reader, driveID string, parentComponents []string, deadline time.Time, queue *filesVisualizationQueue, limit int) (bool, error) {
	parentKey := metadataPathKey(parentComponents)
	iter, err := reader.NewIter(&pebble.IterOptions{LowerBound: parentKey, UpperBound: metadataPrefixSuccessor(parentKey)})
	if err != nil {
		return false, err
	}
	defer iter.Close()

	complete := true
	for valid := iter.SeekGE(parentKey); valid; {
		if time.Now().After(deadline) {
			return false, nil
		}
		key := append([]byte(nil), iter.Key()...)
		if bytes.Equal(key, parentKey) {
			valid = iter.Next()
			continue
		}
		components, keyErr := metadataPathComponents(key)
		if keyErr != nil {
			return false, keyErr
		}
		if len(components) != len(parentComponents)+1 {
			valid = iter.SeekGE(metadataPrefixSuccessor(metadataPathKey(components[:len(parentComponents)+1])))
			continue
		}
		var entry MetadataEntry
		if decodeErr := entry.Decode(iter.Value()); decodeErr != nil {
			return false, decodeErr
		}
		heap.Push(queue, filesVisualizationCandidate{
			path:  "/" + driveID + "/" + strings.Join(components, "/"),
			entry: entry,
		})
		if queue.Len() > limit {
			heap.Pop(queue)
			complete = false
		}
		valid = iter.SeekGE(metadataPrefixSuccessor(key))
	}
	if err := iter.Error(); err != nil {
		return false, err
	}
	return complete, nil
}

func filesVisualizationApiEntry(path string, entry MetadataEntry) orc.FilesVisualizeEntry {
	entryType := orc.FileTypeFile
	if entry.EntryType == MetaEntryDirectory {
		entryType = orc.FileTypeDirectory
	}
	return orc.FilesVisualizeEntry{Path: path, Type: entryType, SizeInBytes: filesVisualizationEntrySize(entry)}
}

func filesVisualizationEntrySize(entry MetadataEntry) uint64 {
	if entry.EntryType == MetaEntryDirectory {
		return entry.RecursiveLogicalBytes
	}
	return entry.LogicalSize
}
