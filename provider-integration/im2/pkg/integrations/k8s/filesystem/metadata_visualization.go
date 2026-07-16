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
		return q[i].path < q[j].path
	}
	return iSize > jSize
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

	driveRoot := "/" + request.ResolvedCollection.Id
	_, indexed, err := MetadataLookupDirectoryStats(driveRoot)
	if err != nil {
		return orc.FilesVisualizeResponse{}, util.ServerHttpError("failed to query storage metadata")
	}
	if !indexed {
		completed := make(chan error, 1)
		metadataSubmitScanRequest(request.Path, func(scanErr error) { completed <- scanErr })
		select {
		case scanErr := <-completed:
			if scanErr != nil {
				return orc.FilesVisualizeResponse{Entries: []orc.FilesVisualizeEntry{}}, nil
			}
		case <-time.After(filesVisualizationIndexWait):
		}

		_, indexed, err = MetadataLookupDirectoryStats(driveRoot)
		if err != nil {
			return orc.FilesVisualizeResponse{}, util.ServerHttpError("failed to query storage metadata")
		}
		if !indexed {
			return orc.FilesVisualizeResponse{Entries: []orc.FilesVisualizeEntry{}}, nil
		}
	}

	entries, observedAt, complete, found, err := metadataVisualize(request.Path, filesVisualizationMaxEntries, filesVisualizationMaxDuration)
	if err != nil {
		return orc.FilesVisualizeResponse{}, util.ServerHttpError("failed to query storage metadata")
	}
	if !found {
		return orc.FilesVisualizeResponse{}, util.HttpErr(http.StatusNotFound, "path is not present in the metadata catalog")
	}
	return orc.FilesVisualizeResponse{
		Entries:       entries,
		LastUpdatedAt: util.OptValue(fnd.Timestamp(time.Unix(0, observedAt))),
		Complete:      complete,
	}, nil
}

func metadataVisualize(ucloudPath string, limit int, maxDuration time.Duration) (result []orc.FilesVisualizeEntry, observedAt int64, complete, found bool, err error) {
	internalPath, ok, drive := UCloudToInternal(ucloudPath)
	if !ok {
		return nil, 0, false, false, fmt.Errorf("unknown UCloud path %q", ucloudPath)
	}
	driveRoot, ok, _ := DriveToLocalPath(drive)
	if !ok {
		return nil, 0, false, false, fmt.Errorf("drive %q is not available on this provider", drive.Id)
	}
	rootComponents, err := metadataComponentsBelowDrive(internalPath, driveRoot)
	if err != nil {
		return nil, 0, false, false, err
	}
	db, closeDB, err := metadataOpenDatabaseForQuery(drive.Id)
	if err != nil {
		if errors.Is(err, errMetadataCatalogNotFound) {
			return []orc.FilesVisualizeEntry{}, 0, false, false, nil
		}
		return nil, 0, false, false, err
	}
	defer closeDB()
	return metadataVisualizeInDB(db, drive.Id, rootComponents, limit, maxDuration)
}

func metadataVisualizeInDB(db *pebble.DB, driveID string, rootComponents []string, limit int, maxDuration time.Duration) (result []orc.FilesVisualizeEntry, observedAt int64, complete, found bool, err error) {
	rootEntry, found, err := metadataReadEntry(db, metadataPathKey(rootComponents))
	if err != nil || !found {
		return nil, 0, false, found, err
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
	}

	queue := &filesVisualizationQueue{}
	heap.Init(queue)
	// Expanding the largest queued directory first gives the entry budget to the most significant subtrees.
	children, childrenComplete, listErr := metadataVisualizationChildren(db, driveID, rootComponents, deadline)
	if listErr != nil {
		return nil, 0, false, true, listErr
	}
	for _, child := range children {
		heap.Push(queue, child)
	}
	complete = childrenComplete

	for queue.Len() > 0 && len(result) < limit && time.Now().Before(deadline) {
		candidate := heap.Pop(queue).(filesVisualizationCandidate)
		result = append(result, filesVisualizationApiEntry(candidate.path, candidate.entry))
		if candidate.entry.EntryType != MetaEntryDirectory {
			continue
		}

		components := util.Components(candidate.path)[1:]
		children, listedAll, listErr := metadataVisualizationChildren(db, driveID, components, deadline)
		if listErr != nil {
			return nil, 0, false, true, listErr
		}
		complete = complete && listedAll
		for _, child := range children {
			heap.Push(queue, child)
		}
	}
	complete = complete && queue.Len() == 0
	return result, observedAt, complete, true, nil
}

func metadataVisualizationChildren(db *pebble.DB, driveID string, parentComponents []string, deadline time.Time) ([]filesVisualizationCandidate, bool, error) {
	parentKey := metadataPathKey(parentComponents)
	iter, err := db.NewIter(&pebble.IterOptions{LowerBound: parentKey, UpperBound: metadataPrefixSuccessor(parentKey)})
	if err != nil {
		return nil, false, err
	}
	defer iter.Close()

	var result []filesVisualizationCandidate
	for valid := iter.SeekGE(parentKey); valid; {
		if time.Now().After(deadline) {
			return result, false, nil
		}
		key := append([]byte(nil), iter.Key()...)
		if bytes.Equal(key, parentKey) {
			valid = iter.Next()
			continue
		}
		components, keyErr := metadataPathComponents(key)
		if keyErr != nil {
			return nil, false, keyErr
		}
		if len(components) != len(parentComponents)+1 {
			valid = iter.SeekGE(metadataPrefixSuccessor(metadataPathKey(components[:len(parentComponents)+1])))
			continue
		}
		var entry MetadataEntry
		if decodeErr := entry.Decode(iter.Value()); decodeErr != nil {
			return nil, false, decodeErr
		}
		result = append(result, filesVisualizationCandidate{
			path:  "/" + driveID + "/" + strings.Join(components, "/"),
			entry: entry,
		})
		valid = iter.SeekGE(metadataPrefixSuccessor(key))
	}
	if err := iter.Error(); err != nil {
		return nil, false, err
	}
	return result, true, nil
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
