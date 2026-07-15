package filesystem

import (
	"bufio"
	"bytes"
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"
	"unicode/utf8"

	"github.com/cockroachdb/pebble/v2"
	"golang.org/x/sys/unix"
	"golang.org/x/text/cases"
	"golang.org/x/text/unicode/norm"
	"golang.org/x/time/rate"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const MetadataFormatVersion = 1

type MetaEntryType uint8

const (
	MetaEntryUnknown   MetaEntryType = 0
	MetaEntryRegular   MetaEntryType = 1
	MetaEntryDirectory MetaEntryType = 2
	MetaEntrySymlink   MetaEntryType = 3
	MetaEntryOther     MetaEntryType = 4
)

type MetaEntryFlag uint16

const (
	MetaFlagHasAllocatedSize MetaEntryFlag = 1 << iota
	MetaFlagHasAccessTime
	MetaFlagHasInodeIdentity
	MetaFlagHasMode
	MetaFlagHasLinkCount
)

const metadataKnownFlags = MetaFlagHasAllocatedSize | MetaFlagHasAccessTime | MetaFlagHasInodeIdentity | MetaFlagHasMode | MetaFlagHasLinkCount

type MetadataEntry struct {
	EntryType          MetaEntryType
	EntryGeneration    uint64
	ObservedAtUnixNano int64
	LogicalSize        uint64
	AllocatedSize      util.Option[uint64]
	ModificationTime   int64
	AccessTime         util.Option[int64]
	DeviceID           util.Option[uint64]
	InodeID            util.Option[uint64]
	Mode               util.Option[uint64]
	LinkCount          util.Option[uint64]

	AggregateGeneration     uint64
	AggregateObservedAt     int64
	RecursiveLogicalBytes   uint64
	RecursiveAllocatedSize  util.Option[uint64]
	RecursiveFileCount      uint64
	RecursiveDirectoryCount uint64
}

func (e *MetadataEntry) Encode() []byte {
	flags := MetaEntryFlag(0)
	if e.AllocatedSize.Present {
		flags |= MetaFlagHasAllocatedSize
	}
	if e.AccessTime.Present {
		flags |= MetaFlagHasAccessTime
	}
	if e.DeviceID.Present && e.InodeID.Present {
		flags |= MetaFlagHasInodeIdentity
	}
	if e.Mode.Present {
		flags |= MetaFlagHasMode
	}
	if e.LinkCount.Present {
		flags |= MetaFlagHasLinkCount
	}

	result := make([]byte, 4, 96)
	result[0] = MetadataFormatVersion
	result[1] = byte(e.EntryType)
	binary.LittleEndian.PutUint16(result[2:4], uint16(flags))
	result = binary.AppendUvarint(result, e.EntryGeneration)
	result = binary.AppendVarint(result, e.ObservedAtUnixNano)
	result = binary.AppendUvarint(result, e.LogicalSize)
	if flags&MetaFlagHasAllocatedSize != 0 {
		result = binary.AppendUvarint(result, e.AllocatedSize.Value)
	}
	result = binary.AppendVarint(result, e.ModificationTime)
	if flags&MetaFlagHasAccessTime != 0 {
		result = binary.AppendVarint(result, e.AccessTime.Value)
	}
	if flags&MetaFlagHasInodeIdentity != 0 {
		result = binary.AppendUvarint(result, e.DeviceID.Value)
		result = binary.AppendUvarint(result, e.InodeID.Value)
	}
	if flags&MetaFlagHasMode != 0 {
		result = binary.AppendUvarint(result, e.Mode.Value)
	}
	if flags&MetaFlagHasLinkCount != 0 {
		result = binary.AppendUvarint(result, e.LinkCount.Value)
	}
	if e.EntryType == MetaEntryDirectory {
		result = binary.AppendUvarint(result, e.AggregateGeneration)
		result = binary.AppendVarint(result, e.AggregateObservedAt)
		result = binary.AppendUvarint(result, e.RecursiveLogicalBytes)
		if flags&MetaFlagHasAllocatedSize != 0 {
			result = binary.AppendUvarint(result, e.RecursiveAllocatedSize.GetOrDefault(0))
		}
		result = binary.AppendUvarint(result, e.RecursiveFileCount)
		result = binary.AppendUvarint(result, e.RecursiveDirectoryCount)
	}
	return result
}

func (e *MetadataEntry) Decode(data []byte) error {
	if len(data) < 4 {
		return io.ErrUnexpectedEOF
	}
	if data[0] != MetadataFormatVersion {
		return fmt.Errorf("unsupported metadata format version %d", data[0])
	}
	entryType := MetaEntryType(data[1])
	if entryType < MetaEntryRegular || entryType > MetaEntryOther {
		return fmt.Errorf("invalid metadata entry type %d", entryType)
	}
	flags := MetaEntryFlag(binary.LittleEndian.Uint16(data[2:4]))
	if flags&^metadataKnownFlags != 0 {
		return fmt.Errorf("unknown metadata flags 0x%x", uint16(flags&^metadataKnownFlags))
	}
	if flags&MetaFlagHasInodeIdentity != 0 {
		// Both values are encoded together, so Decode never creates half an identity.
	}

	decoder := metadataValueDecoder{data: data[4:]}
	decoded := MetadataEntry{EntryType: entryType}
	decoded.EntryGeneration = decoder.uvarint()
	decoded.ObservedAtUnixNano = decoder.varint()
	decoded.LogicalSize = decoder.uvarint()
	if flags&MetaFlagHasAllocatedSize != 0 {
		decoded.AllocatedSize = util.OptValue(decoder.uvarint())
	}
	decoded.ModificationTime = decoder.varint()
	if flags&MetaFlagHasAccessTime != 0 {
		decoded.AccessTime = util.OptValue(decoder.varint())
	}
	if flags&MetaFlagHasInodeIdentity != 0 {
		decoded.DeviceID = util.OptValue(decoder.uvarint())
		decoded.InodeID = util.OptValue(decoder.uvarint())
	}
	if flags&MetaFlagHasMode != 0 {
		decoded.Mode = util.OptValue(decoder.uvarint())
	}
	if flags&MetaFlagHasLinkCount != 0 {
		decoded.LinkCount = util.OptValue(decoder.uvarint())
	}
	if entryType == MetaEntryDirectory {
		decoded.AggregateGeneration = decoder.uvarint()
		decoded.AggregateObservedAt = decoder.varint()
		decoded.RecursiveLogicalBytes = decoder.uvarint()
		if flags&MetaFlagHasAllocatedSize != 0 {
			decoded.RecursiveAllocatedSize = util.OptValue(decoder.uvarint())
		}
		decoded.RecursiveFileCount = decoder.uvarint()
		decoded.RecursiveDirectoryCount = decoder.uvarint()
	}
	if decoder.err != nil {
		return decoder.err
	}
	if len(decoder.data) != 0 {
		return fmt.Errorf("metadata value has %d trailing bytes", len(decoder.data))
	}
	*e = decoded
	return nil
}

type metadataValueDecoder struct {
	data []byte
	err  error
}

func (d *metadataValueDecoder) uvarint() uint64 {
	if d.err != nil {
		return 0
	}
	value, count := binary.Uvarint(d.data)
	if count <= 0 {
		d.err = io.ErrUnexpectedEOF
		if count < 0 {
			d.err = errors.New("metadata uvarint overflows uint64")
		}
		return 0
	}
	d.data = d.data[count:]
	return value
}

func (d *metadataValueDecoder) varint() int64 {
	if d.err != nil {
		return 0
	}
	value, count := binary.Varint(d.data)
	if count <= 0 {
		d.err = io.ErrUnexpectedEOF
		if count < 0 {
			d.err = errors.New("metadata varint overflows int64")
		}
		return 0
	}
	d.data = d.data[count:]
	return value
}

const (
	MetaKeyspacePath = 0x01
	MetaKeyspaceName = 0x02
)

var errMetadataScanInvalidated = errors.New("metadata scan invalidated by a live filesystem change")
var metadataPendingAggregateKey = []byte{0x00, 0x01}
var metadataPendingNameKey = []byte{0x00, 0x02}

type MetadataCatalogConfig struct {
	IOPS              int
	ParallelScans     int
	EntriesPerSSTable int
}

var metadataRuntime = struct {
	sync.Mutex
	started  bool
	config   MetadataCatalogConfig
	limiter  *rate.Limiter
	requests chan metadataScanRequest
}{config: MetadataCatalogConfig{IOPS: 30_000, ParallelScans: 8, EntriesPerSSTable: 100_000}}

var metadataCompleteCoverage sync.Map
var metadataLastGeneration atomic.Uint64
var metadataNameRefreshes sync.Map
var metadataDatabases = struct {
	sync.Mutex
	open map[string]*metadataOpenDatabase
}{open: map[string]*metadataOpenDatabase{}}

type metadataOpenDatabase struct {
	db   *pebble.DB
	refs int
}

type metadataScanRequest struct {
	internalPath string
	drive        *orc.Drive
}

// MetadataConfigureCatalog configures the aggregate scan budget. Call it before the first request.
func MetadataConfigureCatalog(config MetadataCatalogConfig) error {
	if config.IOPS <= 0 || config.ParallelScans <= 1 || config.EntriesPerSSTable < 10_000 {
		return errors.New("metadata catalog requires positive IOPS, at least 10,000 entries per SSTable, and 2 parallel scans")
	}
	metadataRuntime.Lock()
	defer metadataRuntime.Unlock()
	if metadataRuntime.started {
		return errors.New("metadata catalog is already running")
	}
	metadataRuntime.config = config
	return nil
}

func MetadataSubmitScanRequest(ucloudPath string) {
	internalPath, ok, drive := UCloudToInternal(ucloudPath)
	if !ok {
		return
	}
	if !metadataHasCompleteCoverage(drive.Id) {
		internalPath, _, _ = UCloudToInternal(fmt.Sprintf("/%v", drive.Id))
	}

	metadataStartRuntime()
	metadataRecordScanSubmitted(drive.Id)
	metadataRuntime.requests <- metadataScanRequest{internalPath: internalPath, drive: drive}
}

func metadataStartRuntime() {
	metadataRuntime.Lock()
	defer metadataRuntime.Unlock()
	if metadataRuntime.started {
		return
	}
	config := metadataRuntime.config
	metadataRuntime.limiter = rate.NewLimiter(rate.Limit(config.IOPS), max(1, config.IOPS/10))
	metadataRuntime.requests = make(chan metadataScanRequest, 1024)
	metadataRuntime.started = true

	ready := make(chan metadataScanRequest, 1024)
	done := make(chan string, config.ParallelScans)
	go metadataDispatchScans(metadataRuntime.requests, ready, done)
	for range config.ParallelScans {
		go func() {
			for request := range ready {
				metadataDoScan(request.internalPath, request.drive)
				done <- request.drive.Id
			}
		}()
	}
}

func metadataDispatchScans(requests <-chan metadataScanRequest, ready chan<- metadataScanRequest, done <-chan string) {
	active := map[string]bool{}
	pending := map[string][]metadataScanRequest{}
	var runnable []metadataScanRequest
	for {
		var readyChannel chan<- metadataScanRequest
		var next metadataScanRequest
		if len(runnable) > 0 {
			readyChannel = ready
			next = runnable[0]
		}
		select {
		case request := <-requests:
			if active[request.drive.Id] {
				pending[request.drive.Id] = append(pending[request.drive.Id], request)
			} else {
				active[request.drive.Id] = true
				runnable = append(runnable, request)
			}
		case readyChannel <- next:
			runnable = runnable[1:]
		case driveID := <-done:
			queue := pending[driveID]
			if len(queue) == 0 {
				delete(active, driveID)
				delete(pending, driveID)
			} else {
				runnable = append(runnable, queue[0])
				pending[driveID] = queue[1:]
			}
		}
	}
}

// metadataHasCompleteCoverage returns true if a complete drive-root scan has been published.
func metadataHasCompleteCoverage(driveID string) bool {
	if _, ok := metadataCompleteCoverage.Load(driveID); ok {
		return true
	}
	databasePath := metadataDatabasePath(driveID)
	db, release, err := metadataAcquireDatabase(databasePath, false)
	if err != nil {
		return false
	}
	defer release()
	_, closer, err := db.Get([]byte{MetaKeyspacePath})
	if err != nil {
		return false
	}
	_ = closer.Close()
	_, pendingCloser, pendingErr := db.Get(metadataPendingAggregateKey)
	if pendingCloser != nil {
		_ = pendingCloser.Close()
	}
	if pendingErr == nil || !errors.Is(pendingErr, pebble.ErrNotFound) {
		return false
	}
	metadataCompleteCoverage.Store(driveID, true)
	return true
}

func metadataDoScan(internalPath string, drive *orc.Drive) {
	basePath, ok, _ := DriveToLocalPath(drive)
	if !ok {
		return
	}
	metadataRuntime.Lock()
	limiter := metadataRuntime.limiter
	entriesPerTable := metadataRuntime.config.EntriesPerSSTable
	metadataRuntime.Unlock()
	startedAt := time.Now()
	metadataRecordScanStarted(drive.Id)
	err := metadataScanAndPublish(context.Background(), internalPath, basePath, metadataDatabasePath(drive.Id), limiter, entriesPerTable)
	metadataRecordScanFinished(drive.Id, time.Since(startedAt), err)
	if err != nil {
		if !metadataExpectedLiveTreeError(err) && !errors.Is(err, errMetadataScanInvalidated) {
			log.Warn("Metadata scan of drive %s at %s failed: %s", drive.Id, internalPath, err)
		}
	} else if filepath.Clean(internalPath) == filepath.Clean(basePath) {
		metadataCompleteCoverage.Store(drive.Id, true)
	}
}

func metadataDatabasePath(driveID string) string {
	mnt := shared.ServiceConfig.FileSystem.MountPoint
	return filepath.Join(mnt, "metadata-catalog", driveID)
}

type metadataAggregate struct {
	logical     uint64
	allocated   uint64
	files       uint64
	directories uint64
}

type metadataRecord struct {
	key   []byte
	value []byte
}

type metadataScanOutput struct {
	runs        []string
	chunk       []metadataRecord
	tempDir     string
	nextRun     int
	chunkSize   int
	scanRootKey []byte
	rootEntry   MetadataEntry
}

func metadataScanAndPublish(ctx context.Context, scanPath, driveRoot, databasePath string, limiter *rate.Limiter, entriesPerTable int) error {
	// NOTE(Dan): A scan must not publish anything until the complete subtree has been visited. Users are changing the
	// filesystem while we scan it, and a partial result would make deleted files disappear before their replacement is
	// known. We first build the complete replacement in a temporary workspace and only open Pebble when it is ready.
	relative, err := filepath.Rel(filepath.Clean(driveRoot), filepath.Clean(scanPath))
	if err != nil || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
		return fmt.Errorf("scan root %q is outside drive root %q", scanPath, driveRoot)
	}
	components := metadataRelativeComponents(relative)
	if err := os.MkdirAll(databasePath, 0o700); err != nil {
		return fmt.Errorf("create metadata database directory: %w", err)
	}
	tempDir, err := os.MkdirTemp(databasePath, ".scan-")
	if err != nil {
		return fmt.Errorf("create scan workspace: %w", err)
	}
	cleanupTemp := true
	defer func() {
		if cleanupTemp {
			_ = os.RemoveAll(tempDir)
		}
	}()

	rootKey := metadataPathKey(components)
	output := metadataScanOutput{tempDir: tempDir, chunkSize: entriesPerTable, scanRootKey: rootKey}
	generation := metadataNextGeneration()
	directory, info, err := metadataOpenScanRoot(ctx, driveRoot, components, limiter)
	if err != nil {
		return err
	}
	if _, err = metadataScanDirectory(ctx, scanPath, components, generation, limiter, &output, directory, info); err != nil {
		_ = directory.Close()
		return err
	}
	if err = metadataRevalidateScanRoot(ctx, driveRoot, components, info, limiter); err != nil {
		_ = directory.Close()
		return err
	}
	if err = directory.Close(); err != nil {
		return err
	}

	// The traversal order is chosen by the filesystem and is not suitable for an SSTable. Runs keep memory usage
	// bounded while the final merge gives Pebble the sorted and non-overlapping tables required for ingestion.
	if err = output.flushRun(); err != nil {
		return err
	}
	sstables, err := metadataMergeRunsToSSTables(output.runs, tempDir, entriesPerTable)
	if err != nil {
		return err
	}

	db, releaseDatabase, err := metadataAcquireDatabase(databasePath, true)
	if err != nil {
		return fmt.Errorf("open metadata database: %w", err)
	}
	databaseOpen := true
	defer func() {
		if databaseOpen {
			releaseDatabase()
		}
	}()

	// NOTE(Dan): The process may have stopped after publishing an earlier PATH replacement but before updating its
	// ancestors or NAME entries. Recover that work before calculating another delta, otherwise a later scan could make
	// the incomplete update impossible to distinguish from a completed one.
	if err = metadataRecoverPendingAggregate(db); err != nil {
		return err
	}
	if err = metadataRecoverPendingNameIndex(db); err != nil {
		return err
	}
	span := pebble.KeyRange{Start: rootKey, End: metadataPrefixSuccessor(rootKey)}
	oldRoot, oldPresent, err := metadataReadEntry(db, rootKey)
	if err != nil {
		return err
	}
	oldNamesPath := filepath.Join(tempDir, "old-names")
	if err = metadataSpoolOldNameKeys(db, span, oldNamesPath); err != nil {
		return err
	}
	oldContribution := metadataEntryContribution(oldRoot, oldPresent)
	newContribution := metadataEntryContribution(output.rootEntry, true)
	pending := metadataPendingAggregate{generation: generation, rootKey: rootKey, old: oldContribution, new: newContribution}

	// The PATH replacement and ancestor updates are separate Pebble operations. The journal makes this safe across a
	// crash. It is written before ingestion and removed in the same batch that applies the ancestor delta.
	journal := db.NewBatch()
	if err = journal.Set(metadataPendingAggregateKey, pending.encode(), nil); err == nil {
		err = journal.Set(metadataPendingNameKey, rootKey, nil)
	}
	if err == nil {
		err = journal.Commit(pebble.Sync)
	}
	_ = journal.Close()
	if err != nil {
		return fmt.Errorf("journal pending aggregate update: %w", err)
	}

	// IngestAndExcise is the point where the completed scan becomes visible. Everything below the scan root is replaced
	// at once, which also removes entries for files and directories that were not present in the new scan.
	if _, err = db.IngestAndExcise(ctx, sstables, nil, nil, span); err != nil {
		return fmt.Errorf("publish metadata subtree: %w", err)
	}
	newRoot, newPresent, err := metadataReadEntry(db, rootKey)
	if err != nil || !newPresent {
		return fmt.Errorf("read published scan root: %w", err)
	}
	if newRoot.EntryGeneration != generation {
		return errors.New("published scan root has an unexpected generation")
	}
	publishedContribution := metadataEntryContribution(newRoot, true)
	if err = metadataUpdateAncestors(db, components, oldContribution, publishedContribution, generation); err != nil {
		return err
	}

	pathBytes, _ := db.EstimateDiskUsage([]byte{MetaKeyspacePath}, []byte{MetaKeyspaceName})
	nameBytes, _ := db.EstimateDiskUsage([]byte{MetaKeyspaceName}, []byte{MetaKeyspaceName + 1})
	metadataRecordScanContents(filepath.Base(databasePath), output.rootEntry, db.Metrics().DiskSpaceUsage(), pathBytes, nameBytes)
	releaseDatabase()
	databaseOpen = false

	// NAME entries are not contiguous for a path subtree, so they cannot be part of the excised span. PATH remains the
	// authoritative index while NAME catches up. Search results must verify PATH entries and may remove stale hits.
	cleanupTemp = false
	refreshDone := make(chan struct{})
	metadataNameRefreshes.Store(databasePath, refreshDone)
	metadataRecordNameRefreshStarted(filepath.Base(databasePath))
	go metadataRefreshNameIndexAsync(databasePath, filepath.Base(databasePath), span, oldNamesPath, tempDir, scanPath)
	return nil
}

func metadataScanDirectory(ctx context.Context, path string, components []string, generation uint64, limiter *rate.Limiter, output *metadataScanOutput, directory *os.File, info os.FileInfo) (metadataAggregate, error) {
	if !info.IsDir() {
		return metadataAggregate{}, fmt.Errorf("scan root %q is not a directory", path)
	}

	directoryEntry := metadataEntryFromFileInfo(info, generation)
	aggregate := metadataAggregate{}

	// NOTE(Dan): ReadDir uses bounded batches because a single directory can contain millions of entries. The limiter
	// accounts for directory reads, metadata lookups, and opening child directories. These are the operations that put
	// pressure on the distributed filesystem during a scan.
	for {
		if err := limiter.Wait(ctx); err != nil {
			return metadataAggregate{}, err
		}
		children, readErr := directory.ReadDir(1024)
		for _, child := range children {
			childPath := filepath.Join(path, child.Name())
			childComponents := append(metadataCloneComponents(components), child.Name())
			if err := limiter.Wait(ctx); err != nil {
				return metadataAggregate{}, err
			}
			childInfo, infoErr := child.Info()
			if infoErr != nil {
				// A file disappearing between ReadDir and Info is normal on a live filesystem. No data for the file has
				// been collected yet, so it is safe to leave it out of this scan without invalidating the subtree.
				if metadataExpectedLiveTreeError(infoErr) {
					continue
				}
				return metadataAggregate{}, fmt.Errorf("stat %q: %w", childPath, infoErr)
			}
			if childInfo.IsDir() {
				// Open directories relative to their parent and do not follow symlinks. A user may replace or rename a
				// directory while it is being scanned. In that case we reject the complete scan instead of publishing
				// records under a path which no longer refers to the directory we visited.
				if err := limiter.Wait(ctx); err != nil {
					return metadataAggregate{}, err
				}
				fd, openErr := unix.Openat(int(directory.Fd()), child.Name(), unix.O_RDONLY|unix.O_DIRECTORY|unix.O_NOFOLLOW|unix.O_CLOEXEC, 0)
				if openErr != nil {
					if metadataExpectedLiveTreeError(openErr) {
						return metadataAggregate{}, fmt.Errorf("%w: directory %q changed before opening", errMetadataScanInvalidated, childPath)
					}
					return metadataAggregate{}, fmt.Errorf("open directory %q: %w", childPath, openErr)
				}
				childDirectory := os.NewFile(uintptr(fd), childPath)
				openedInfo, statErr := childDirectory.Stat()
				if statErr != nil || !os.SameFile(childInfo, openedInfo) {
					_ = childDirectory.Close()
					return metadataAggregate{}, fmt.Errorf("%w: directory %q changed while opening", errMetadataScanInvalidated, childPath)
				}
				childAggregate, scanErr := metadataScanDirectory(ctx, childPath, childComponents, generation, limiter, output, childDirectory, childInfo)
				if scanErr != nil {
					_ = childDirectory.Close()
					return metadataAggregate{}, scanErr
				}
				if !metadataDirectoryStillBound(directory, child.Name(), childInfo) {
					_ = childDirectory.Close()
					return metadataAggregate{}, fmt.Errorf("%w: directory %q changed during traversal", errMetadataScanInvalidated, childPath)
				}
				if closeErr := childDirectory.Close(); closeErr != nil {
					return metadataAggregate{}, closeErr
				}
				aggregate.logical += childAggregate.logical
				aggregate.allocated += childAggregate.allocated
				aggregate.files += childAggregate.files
				aggregate.directories += childAggregate.directories
				continue
			}

			entry := metadataEntryFromFileInfo(childInfo, generation)
			aggregate.addEntry(entry)
			if err := output.append(metadataRecord{key: metadataPathKey(childComponents), value: entry.Encode()}); err != nil {
				return metadataAggregate{}, err
			}
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			if metadataExpectedLiveTreeError(readErr) {
				return metadataAggregate{}, fmt.Errorf("%w: read directory %q: %v", errMetadataScanInvalidated, path, readErr)
			}
			return metadataAggregate{}, fmt.Errorf("read directory %q: %w", path, readErr)
		}
	}

	// Directory records are emitted after their children. This lets each record contain a complete recursive aggregate
	// without keeping the full subtree in memory. The returned aggregate includes this directory so its parent can add
	// the complete contribution with a small fixed amount of work.
	directoryEntry.AggregateGeneration = generation
	directoryEntry.AggregateObservedAt = time.Now().UnixNano()
	directoryEntry.RecursiveLogicalBytes = aggregate.logical
	directoryEntry.RecursiveAllocatedSize = util.OptValue(aggregate.allocated)
	directoryEntry.RecursiveFileCount = aggregate.files
	directoryEntry.RecursiveDirectoryCount = aggregate.directories
	directoryKey := metadataPathKey(components)
	if bytes.Equal(directoryKey, output.scanRootKey) {
		output.rootEntry = directoryEntry
	}
	if err := output.append(metadataRecord{key: directoryKey, value: directoryEntry.Encode()}); err != nil {
		return metadataAggregate{}, err
	}
	aggregate.addEntry(directoryEntry)
	return aggregate, nil
}

func (a *metadataAggregate) addEntry(entry MetadataEntry) {
	a.logical += entry.LogicalSize
	a.allocated += entry.AllocatedSize.GetOrDefault(0)
	if entry.EntryType == MetaEntryDirectory {
		a.directories++
	} else {
		a.files++
	}
}

func metadataEntryFromFileInfo(info os.FileInfo, generation uint64) MetadataEntry {
	entryType := MetaEntryOther
	switch {
	case info.IsDir():
		entryType = MetaEntryDirectory
	case info.Mode().IsRegular():
		entryType = MetaEntryRegular
	case info.Mode()&os.ModeSymlink != 0:
		entryType = MetaEntrySymlink
	}
	entry := MetadataEntry{
		EntryType:          entryType,
		EntryGeneration:    generation,
		ObservedAtUnixNano: time.Now().UnixNano(),
		LogicalSize:        uint64(max(0, info.Size())),
		ModificationTime:   info.ModTime().UnixNano(),
		Mode:               util.OptValue(uint64(info.Mode())),
	}
	if stat, ok := info.Sys().(*syscall.Stat_t); ok {
		entry.AllocatedSize = util.OptValue(uint64(max(0, stat.Blocks)) * 512)
		entry.AccessTime = util.OptValue(stat.Atim.Sec*int64(time.Second) + stat.Atim.Nsec)
		entry.DeviceID = util.OptValue(uint64(stat.Dev))
		entry.InodeID = util.OptValue(stat.Ino)
		entry.LinkCount = util.OptValue(uint64(stat.Nlink))
	}
	return entry
}

func metadataExpectedLiveTreeError(err error) bool {
	return errors.Is(err, os.ErrNotExist) || errors.Is(err, syscall.ESTALE) || errors.Is(err, syscall.ENOTDIR) || errors.Is(err, syscall.ELOOP) || errors.Is(err, syscall.EPERM) || strings.Contains(err.Error(), "permission denied")
}

func metadataNextGeneration() uint64 {
	now := uint64(time.Now().UnixNano())
	for {
		previous := metadataLastGeneration.Load()
		next := max(now, previous+1)
		if metadataLastGeneration.CompareAndSwap(previous, next) {
			return next
		}
	}
}

func metadataDirectoryStillBound(parent *os.File, name string, expected os.FileInfo) bool {
	stat, ok := expected.Sys().(*syscall.Stat_t)
	if !ok {
		return false
	}
	var current unix.Stat_t
	if err := unix.Fstatat(int(parent.Fd()), name, &current, unix.AT_SYMLINK_NOFOLLOW); err != nil {
		return false
	}
	return uint64(current.Dev) == uint64(stat.Dev) && current.Ino == stat.Ino && current.Mode&unix.S_IFMT == unix.S_IFDIR
}

func metadataRevalidateScanRoot(ctx context.Context, driveRoot string, components []string, expected os.FileInfo, limiter *rate.Limiter) error {
	directory, current, err := metadataOpenScanRoot(ctx, driveRoot, components, limiter)
	if err != nil {
		if metadataExpectedLiveTreeError(err) {
			return fmt.Errorf("%w: scan root changed during traversal", errMetadataScanInvalidated)
		}
		return err
	}
	_ = directory.Close()
	if !os.SameFile(expected, current) {
		return fmt.Errorf("%w: scan root changed during traversal", errMetadataScanInvalidated)
	}
	return nil
}

func metadataOpenScanRoot(ctx context.Context, driveRoot string, components []string, limiter *rate.Limiter) (*os.File, os.FileInfo, error) {
	if err := limiter.Wait(ctx); err != nil {
		return nil, nil, err
	}
	fd, err := unix.Open(filepath.Clean(driveRoot), unix.O_RDONLY|unix.O_DIRECTORY|unix.O_NOFOLLOW|unix.O_CLOEXEC, 0)
	if err != nil {
		return nil, nil, fmt.Errorf("open drive root %q: %w", driveRoot, err)
	}
	directory := os.NewFile(uintptr(fd), driveRoot)
	for index, component := range components {
		if err = limiter.Wait(ctx); err != nil {
			_ = directory.Close()
			return nil, nil, err
		}
		fd, err = unix.Openat(int(directory.Fd()), component, unix.O_RDONLY|unix.O_DIRECTORY|unix.O_NOFOLLOW|unix.O_CLOEXEC, 0)
		if err != nil {
			_ = directory.Close()
			return nil, nil, fmt.Errorf("open scan root %q: %w", filepath.Join(driveRoot, filepath.Join(components...)), err)
		}
		next := os.NewFile(uintptr(fd), filepath.Join(append([]string{driveRoot}, components[:index+1]...)...))
		_ = directory.Close()
		directory = next
	}
	if err = limiter.Wait(ctx); err != nil {
		_ = directory.Close()
		return nil, nil, err
	}
	info, err := directory.Stat()
	if err != nil {
		_ = directory.Close()
		return nil, nil, fmt.Errorf("stat scan root: %w", err)
	}
	return directory, info, nil
}

func metadataRelativeComponents(relative string) []string {
	if relative == "." || relative == "" {
		return nil
	}
	return strings.Split(relative, string(filepath.Separator))
}

func metadataCloneComponents(components []string) []string {
	return append([]string(nil), components...)
}

func metadataPathKey(components []string) []byte {
	length := 1
	for _, component := range components {
		length += len(component) + 1
	}
	key := make([]byte, 1, length)
	key[0] = MetaKeyspacePath
	for _, component := range components {
		key = append(key, component...)
		key = append(key, 0)
	}
	return key
}

func metadataPrefixSuccessor(prefix []byte) []byte {
	result := append([]byte(nil), prefix...)
	for i := len(result) - 1; i >= 0; i-- {
		if result[i] != 0xff {
			result[i]++
			return result[:i+1]
		}
	}
	return nil
}

func metadataPathComponents(key []byte) ([]string, error) {
	if len(key) == 0 || key[0] != MetaKeyspacePath {
		return nil, errors.New("not a PATH key")
	}
	if len(key) == 1 {
		return nil, nil
	}
	if key[len(key)-1] != 0 {
		return nil, errors.New("unterminated PATH key")
	}
	parts := bytes.Split(key[1:len(key)-1], []byte{0})
	components := make([]string, len(parts))
	for i, part := range parts {
		components[i] = string(part)
	}
	return components, nil
}

func metadataNameKey(pathKey []byte) ([]byte, error) {
	components, err := metadataPathComponents(pathKey)
	if err != nil || len(components) == 0 {
		return nil, err
	}
	basename := []byte(components[len(components)-1])
	key := []byte{MetaKeyspaceName}
	key = append(key, metadataNormalizedName(basename)...)
	key = append(key, 0)
	key = append(key, pathKey[1:]...)
	return key, nil
}

func metadataNormalizedName(name []byte) []byte {
	if utf8.Valid(name) {
		return []byte(cases.Fold().String(norm.NFC.String(string(name))))
	}
	return append([]byte{0xff}, name...)
}

func metadataWaitForNameRefresh(databasePath string) {
	if value, ok := metadataNameRefreshes.Load(databasePath); ok {
		<-value.(chan struct{})
	}
}

func metadataAcquireDatabase(databasePath string, create bool) (*pebble.DB, func(), error) {
	metadataDatabases.Lock()
	if existing := metadataDatabases.open[databasePath]; existing != nil {
		existing.refs++
		metadataDatabases.Unlock()
		return existing.db, metadataDatabaseRelease(databasePath), nil
	}
	if !create {
		if _, err := os.Stat(databasePath); errors.Is(err, os.ErrNotExist) {
			metadataDatabases.Unlock()
			return nil, func() {}, errMetadataCatalogNotFound
		} else if err != nil {
			metadataDatabases.Unlock()
			return nil, func() {}, err
		}
	}
	opts := &pebble.Options{FormatMajorVersion: pebble.FormatNewest}
	opts.ApplyCompressionSettings(func() pebble.DBCompressionSettings {
		return pebble.DBCompressionBalanced
	})
	db, err := pebble.Open(databasePath, opts)
	if err != nil {
		metadataDatabases.Unlock()
		return nil, func() {}, err
	}
	metadataDatabases.open[databasePath] = &metadataOpenDatabase{db: db, refs: 1}
	metadataDatabases.Unlock()
	return db, metadataDatabaseRelease(databasePath), nil
}

func metadataDatabaseRelease(databasePath string) func() {
	return func() {
		metadataDatabases.Lock()
		existing := metadataDatabases.open[databasePath]
		if existing != nil {
			existing.refs--
			if existing.refs == 0 {
				delete(metadataDatabases.open, databasePath)
				_ = existing.db.Close()
			}
		}
		metadataDatabases.Unlock()
	}
}

func metadataReadEntry(db *pebble.DB, key []byte) (MetadataEntry, bool, error) {
	value, closer, err := db.Get(key)
	if errors.Is(err, pebble.ErrNotFound) {
		return MetadataEntry{}, false, nil
	}
	if err != nil {
		return MetadataEntry{}, false, err
	}
	defer closer.Close()
	var entry MetadataEntry
	if err = entry.Decode(value); err != nil {
		return MetadataEntry{}, false, fmt.Errorf("decode metadata at %x: %w", key, err)
	}
	return entry, true, nil
}

func metadataUpdateAncestors(db *pebble.DB, scanComponents []string, oldContribution, newContribution metadataAggregate, generation uint64) error {
	batch := db.NewBatch()
	defer batch.Close()
	for depth := len(scanComponents) - 1; depth >= 0; depth-- {
		key := metadataPathKey(scanComponents[:depth])
		ancestor, present, err := metadataReadEntry(db, key)
		if err != nil {
			return err
		}
		if !present || ancestor.EntryType != MetaEntryDirectory {
			return fmt.Errorf("missing directory ancestor for PATH key %x", key)
		}
		if err = metadataApplyAggregateDelta(&ancestor, oldContribution, newContribution); err != nil {
			return fmt.Errorf("update ancestor PATH key %x: %w", key, err)
		}
		ancestor.AggregateGeneration = generation
		ancestor.AggregateObservedAt = time.Now().UnixNano()
		if err = batch.Set(key, ancestor.Encode(), nil); err != nil {
			return err
		}
	}
	if err := batch.Delete(metadataPendingAggregateKey, nil); err != nil {
		return err
	}
	return batch.Commit(pebble.Sync)
}

type metadataPendingAggregate struct {
	generation uint64
	rootKey    []byte
	old        metadataAggregate
	new        metadataAggregate
}

func (pending metadataPendingAggregate) encode() []byte {
	result := binary.AppendUvarint(nil, pending.generation)
	result = binary.AppendUvarint(result, uint64(len(pending.rootKey)))
	result = append(result, pending.rootKey...)
	for _, aggregate := range []metadataAggregate{pending.old, pending.new} {
		result = binary.AppendUvarint(result, aggregate.logical)
		result = binary.AppendUvarint(result, aggregate.allocated)
		result = binary.AppendUvarint(result, aggregate.files)
		result = binary.AppendUvarint(result, aggregate.directories)
	}
	return result
}

func (pending *metadataPendingAggregate) decode(data []byte) error {
	decoder := metadataValueDecoder{data: data}
	pending.generation = decoder.uvarint()
	rootLength := decoder.uvarint()
	if decoder.err != nil || rootLength > uint64(len(decoder.data)) {
		return errors.New("invalid pending aggregate root key")
	}
	pending.rootKey = append([]byte(nil), decoder.data[:int(rootLength)]...)
	decoder.data = decoder.data[int(rootLength):]
	pending.old = metadataAggregate{logical: decoder.uvarint(), allocated: decoder.uvarint(), files: decoder.uvarint(), directories: decoder.uvarint()}
	pending.new = metadataAggregate{logical: decoder.uvarint(), allocated: decoder.uvarint(), files: decoder.uvarint(), directories: decoder.uvarint()}
	if decoder.err != nil {
		return decoder.err
	}
	if len(decoder.data) != 0 {
		return errors.New("pending aggregate value has trailing bytes")
	}
	return nil
}

func metadataRecoverPendingAggregate(db *pebble.DB) error {
	value, closer, err := db.Get(metadataPendingAggregateKey)
	if errors.Is(err, pebble.ErrNotFound) {
		return nil
	}
	if err != nil {
		return err
	}
	data := append([]byte(nil), value...)
	_ = closer.Close()
	var pending metadataPendingAggregate
	if err = pending.decode(data); err != nil {
		return fmt.Errorf("decode pending aggregate update: %w", err)
	}
	root, present, err := metadataReadEntry(db, pending.rootKey)
	if err != nil {
		return err
	}
	if !present || root.EntryGeneration != pending.generation {
		return db.Delete(metadataPendingAggregateKey, pebble.Sync)
	}
	components, err := metadataPathComponents(pending.rootKey)
	if err != nil {
		return err
	}
	return metadataUpdateAncestors(db, components, pending.old, pending.new, pending.generation)
}

func metadataEntryContribution(entry MetadataEntry, present bool) metadataAggregate {
	if !present {
		return metadataAggregate{}
	}
	result := metadataAggregate{
		logical:     entry.LogicalSize + entry.RecursiveLogicalBytes,
		allocated:   entry.AllocatedSize.GetOrDefault(0) + entry.RecursiveAllocatedSize.GetOrDefault(0),
		files:       entry.RecursiveFileCount,
		directories: entry.RecursiveDirectoryCount,
	}
	if entry.EntryType == MetaEntryDirectory {
		result.directories++
	} else {
		result.files++
	}
	return result
}

func metadataApplyAggregateDelta(entry *MetadataEntry, old, new metadataAggregate) error {
	logical, err := metadataApplyUintDelta(entry.RecursiveLogicalBytes, old.logical, new.logical)
	if err != nil {
		return err
	}
	allocated, err := metadataApplyUintDelta(entry.RecursiveAllocatedSize.GetOrDefault(0), old.allocated, new.allocated)
	if err != nil {
		return err
	}
	files, err := metadataApplyUintDelta(entry.RecursiveFileCount, old.files, new.files)
	if err != nil {
		return err
	}
	directories, err := metadataApplyUintDelta(entry.RecursiveDirectoryCount, old.directories, new.directories)
	if err != nil {
		return err
	}
	entry.RecursiveLogicalBytes = logical
	entry.RecursiveAllocatedSize = util.OptValue(allocated)
	entry.RecursiveFileCount = files
	entry.RecursiveDirectoryCount = directories
	return nil
}

func metadataApplyUintDelta(current, old, new uint64) (uint64, error) {
	if new >= old {
		increase := new - old
		if ^uint64(0)-current < increase {
			return 0, errors.New("aggregate overflow")
		}
		return current + increase, nil
	}
	decrease := old - new
	if current < decrease {
		return 0, errors.New("aggregate underflow")
	}
	return current - decrease, nil
}

func (output *metadataScanOutput) append(record metadataRecord) error {
	output.chunk = append(output.chunk, record)
	if len(output.chunk) >= output.chunkSize {
		return output.flushRun()
	}
	return nil
}

func (output *metadataScanOutput) flushRun() error {
	if len(output.chunk) == 0 {
		return nil
	}
	sort.Slice(output.chunk, func(i, j int) bool { return bytes.Compare(output.chunk[i].key, output.chunk[j].key) < 0 })
	path := filepath.Join(output.tempDir, fmt.Sprintf("run-%06d", output.nextRun))
	output.nextRun++
	file, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	writer := bufio.NewWriterSize(file, 256*1024)
	for _, record := range output.chunk {
		if err = metadataWriteRecord(writer, record); err != nil {
			_ = file.Close()
			return err
		}
	}
	if err = writer.Flush(); err == nil {
		err = file.Sync()
	}
	closeErr := file.Close()
	if err == nil {
		err = closeErr
	}
	if err != nil {
		return err
	}
	output.runs = append(output.runs, path)
	output.chunk = output.chunk[:0]
	return nil
}

func metadataWriteRecord(writer io.Writer, record metadataRecord) error {
	var lengths [20]byte
	n := binary.PutUvarint(lengths[:], uint64(len(record.key)))
	n += binary.PutUvarint(lengths[n:], uint64(len(record.value)))
	if _, err := writer.Write(lengths[:n]); err != nil {
		return err
	}
	if _, err := writer.Write(record.key); err != nil {
		return err
	}
	_, err := writer.Write(record.value)
	return err
}

func metadataReadRecord(reader *bufio.Reader) (metadataRecord, error) {
	keyLength, err := binary.ReadUvarint(reader)
	if err != nil {
		return metadataRecord{}, err
	}
	valueLength, err := binary.ReadUvarint(reader)
	if err != nil {
		return metadataRecord{}, err
	}
	if keyLength > uint64(^uint(0)>>1) || valueLength > uint64(^uint(0)>>1) {
		return metadataRecord{}, errors.New("metadata sort record is too large")
	}
	record := metadataRecord{key: make([]byte, int(keyLength)), value: make([]byte, int(valueLength))}
	if _, err = io.ReadFull(reader, record.key); err != nil {
		return metadataRecord{}, err
	}
	if _, err = io.ReadFull(reader, record.value); err != nil {
		return metadataRecord{}, err
	}
	return record, nil
}
