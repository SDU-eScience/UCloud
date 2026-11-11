package filesystem

import (
	"bufio"
	"bytes"
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"math"
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
	ctrl "ucloud.dk/pkg/controller"
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
	MetaKeyspacePath    = 0x01
	MetaKeyspaceName    = 0x02
	MetaKeyspaceTrigram = 0x03
)

var errMetadataScanInvalidated = errors.New("metadata scan invalidated by a live filesystem change")
var errMetadataAggregateRepairRequired = errors.New("metadata aggregates require a root scan")
var errMetadataNameRepairRequired = errors.New("metadata search index requires repair")
var errMetadataCatalogDeleted = errors.New("metadata catalog has been deleted")
var metadataPendingAggregateKey = []byte{0x00, 0x01}
var metadataPendingNameKey = []byte{0x00, 0x02}
var metadataSearchIndexVersionKey = []byte{0x00, 0x03}
var metadataAggregateVersionKey = []byte{0x00, 0x04}
var metadataSearchIndexVersion = []byte{0x01}
var metadataAggregateVersion = []byte{0x01}

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

var metadataLastGeneration atomic.Uint64
var metadataNameRefreshes sync.Map
var metadataDatabases = struct {
	sync.Mutex
	catalogs   map[string]*metadataCatalogLifecycle
	workspaces map[string]bool
}{
	catalogs:   map[string]*metadataCatalogLifecycle{},
	workspaces: map[string]bool{},
}

type metadataCatalogLifecycle struct {
	publication        sync.Mutex
	deletion           sync.Mutex
	databaseMutex      sync.Mutex
	database           *pebble.DB
	databaseRefs       int
	databaseUsers      int
	coverageGeneration uint64
	coverageValid      bool
	deleted            bool
	drained            chan struct{}
	scans              map[uint64]context.CancelFunc
	nextScan           uint64
}

type metadataScanRequest struct {
	internalPath string
	drive        *orc.Drive
	ensure       bool
	completions  []func(error, time.Time)
}

type metadataScanResult struct {
	driveId    string
	err        error
	finishedAt time.Time
}

type metadataActiveScan struct {
	request    metadataScanRequest
	finished   bool
	err        error
	finishedAt time.Time
}

type metadataScanCompletion struct {
	driveId     string
	err         error
	finishedAt  time.Time
	completions []func(error, time.Time)
}

type metadataInitialization struct {
	done       chan struct{}
	err        error
	finishedAt time.Time
}

var metadataInitializations = struct {
	sync.Mutex
	drives map[string]*metadataInitialization
}{drives: map[string]*metadataInitialization{}}

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
	metadataSubmitScanRequest(ucloudPath, nil)
}

func metadataSubmitScanRequest(ucloudPath string, completion func(error, time.Time)) {
	metadataSubmitScan(ucloudPath, false, completion)
}

func metadataSubmitScan(ucloudPath string, ensure bool, completion func(error, time.Time)) {
	internalPath, ok, drive := UCloudToInternal(ucloudPath)
	if !ok {
		if completion != nil {
			completion(errors.New("unable to resolve metadata scan path"), time.Now())
		}
		return
	}
	coverage := metadataHasCoverageForSubmission(drive.Id)
	if !coverage {
		internalPath, _, _ = UCloudToInternal(fmt.Sprintf("/%v", drive.Id))
	}

	metadataStartRuntime()
	metadataRecordScanSubmitted(drive.Id)
	request := metadataScanRequest{internalPath: internalPath, drive: drive, ensure: ensure}
	if completion != nil {
		request.completions = append(request.completions, completion)
	}
	metadataRuntime.requests <- request
}

func metadataEnsureIndex(driveId string) *metadataInitialization {
	metadataInitializations.Lock()
	initialization := metadataInitializations.drives[driveId]
	if initialization != nil && (initialization.finishedAt.IsZero() || time.Since(initialization.finishedAt) < 30*time.Second) {
		metadataInitializations.Unlock()
		return initialization
	}
	initialization = &metadataInitialization{done: make(chan struct{})}
	metadataInitializations.drives[driveId] = initialization
	metadataInitializations.Unlock()
	go metadataSubmitScan("/"+driveId, true, func(err error, _ time.Time) {
		metadataInitializations.Lock()
		initialization.err = err
		initialization.finishedAt = time.Now()
		if err == nil && metadataInitializations.drives[driveId] == initialization {
			delete(metadataInitializations.drives, driveId)
		}
		close(initialization.done)
		metadataInitializations.Unlock()
	})
	return initialization
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
	done := make(chan metadataScanResult, config.ParallelScans)
	go metadataDispatchScans(metadataRuntime.requests, ready, done)
	for range config.ParallelScans {
		go func() {
			for request := range ready {
				log.Info("Beginning scan of: %v", request.internalPath)
				err := metadataDoScan(request.internalPath, request.drive)
				done <- metadataScanResult{driveId: request.drive.Id, err: err, finishedAt: time.Now()}
			}
		}()
	}
}

func metadataDispatchScans(requests <-chan metadataScanRequest, ready chan<- metadataScanRequest, done <-chan metadataScanResult) {
	const maxPendingCompletions = 1024
	active := map[string]*metadataActiveScan{}
	pending := map[string][]metadataScanRequest{}
	var runnable []metadataScanRequest
	var pendingCompletions []metadataScanCompletion
	callbacks := make(chan metadataScanCompletion)
	completed := make(chan string)
	stopping := make(chan struct{})
	defer close(stopping)
	metadataRuntime.Lock()
	callbackWorkers := metadataRuntime.config.ParallelScans
	metadataRuntime.Unlock()
	for range callbackWorkers {
		go func() {
			for {
				select {
				case completion := <-callbacks:
					for _, callback := range completion.completions {
						callback(completion.err, completion.finishedAt)
					}
					select {
					case completed <- completion.driveId:
					case <-stopping:
						return
					}
				case <-stopping:
					return
				}
			}
		}()
	}
	for {
		var readyChannel chan<- metadataScanRequest
		var next metadataScanRequest
		if len(runnable) > 0 {
			readyChannel = ready
			next = runnable[0]
		}
		var callbackChannel chan<- metadataScanCompletion
		var nextCompletion metadataScanCompletion
		if len(pendingCompletions) > 0 {
			callbackChannel = callbacks
			nextCompletion = pendingCompletions[0]
		}
		var doneChannel <-chan metadataScanResult
		if len(pendingCompletions) < maxPendingCompletions {
			doneChannel = done
		}
		select {
		case request, ok := <-requests:
			if !ok {
				return
			}
			current, running := active[request.drive.Id]
			if running && request.ensure && metadataPathContains(current.request.internalPath, request.internalPath) {
				if current.finished {
					scanErr := current.err
					finishedAt := current.finishedAt
					completions := request.completions
					go func() {
						for _, completion := range completions {
							completion(scanErr, finishedAt)
						}
					}()
				} else {
					current.request.completions = append(current.request.completions, request.completions...)
				}
			} else if running {
				pending[request.drive.Id] = metadataCoalesceScanRequest(pending[request.drive.Id], request)
			} else {
				active[request.drive.Id] = &metadataActiveScan{request: request}
				runnable = append(runnable, request)
			}
		case readyChannel <- next:
			runnable = runnable[1:]
		case callbackChannel <- nextCompletion:
			pendingCompletions[0] = metadataScanCompletion{}
			pendingCompletions = pendingCompletions[1:]
		case result := <-doneChannel:
			current := active[result.driveId]
			current.finished = true
			current.err = result.err
			current.finishedAt = result.finishedAt
			pendingCompletions = append(pendingCompletions, metadataScanCompletion{
				driveId:     result.driveId,
				err:         result.err,
				finishedAt:  result.finishedAt,
				completions: current.request.completions,
			})
		case driveId := <-completed:
			queue := pending[driveId]
			if len(queue) == 0 {
				delete(active, driveId)
				delete(pending, driveId)
			} else {
				active[driveId] = &metadataActiveScan{request: queue[0]}
				runnable = append(runnable, queue[0])
				pending[driveId] = queue[1:]
			}
		}
	}
}

func metadataCoalesceScanRequest(queue []metadataScanRequest, request metadataScanRequest) []metadataScanRequest {
	for i := range queue {
		if metadataPathContains(queue[i].internalPath, request.internalPath) {
			queue[i].completions = append(queue[i].completions, request.completions...)
			return queue
		}
	}

	result := queue[:0]
	for _, existing := range queue {
		if metadataPathContains(request.internalPath, existing.internalPath) {
			request.completions = append(request.completions, existing.completions...)
			continue
		}
		result = append(result, existing)
	}
	return append(result, request)
}

func metadataPathContains(parent, child string) bool {
	relative, err := filepath.Rel(filepath.Clean(parent), filepath.Clean(child))
	return err == nil && relative != ".." && !strings.HasPrefix(relative, ".."+string(filepath.Separator))
}

func metadataCatalogCoverage(driveID string) (bool, bool) {
	databasePath := metadataDatabasePath(driveID)
	db, release, err := metadataAcquireDatabase(databasePath, false)
	if err != nil {
		return false, false
	}
	defer release()
	snapshot := metadataNewQuerySnapshot(db)
	defer snapshot.Close()
	complete, err := metadataAggregatesComplete(snapshot)
	if err != nil || !complete {
		return false, false
	}
	current, err := metadataSearchIndexCurrent(snapshot)
	if err != nil || !current {
		return false, false
	}
	pending, err := metadataNameRefreshPending(snapshot)
	if err != nil {
		return false, false
	}
	return true, !pending
}

func metadataHasCoverageForSubmission(driveId string) bool {
	databasePath := metadataDatabasePath(driveId)
	metadataDatabases.Lock()
	lifecycle := metadataCatalogLifecycleLocked(databasePath)
	if lifecycle.deleted {
		metadataDatabases.Unlock()
		return false
	}
	if lifecycle.coverageValid {
		metadataDatabases.Unlock()
		return true
	}
	generation := lifecycle.coverageGeneration
	metadataDatabases.Unlock()
	coverage, _ := metadataCatalogCoverage(driveId)
	metadataDatabases.Lock()
	if coverage && lifecycle.coverageGeneration == generation && !lifecycle.deleted {
		lifecycle.coverageValid = true
	}
	metadataDatabases.Unlock()
	return coverage
}

func metadataInvalidateCoverage(databasePath string) {
	metadataDatabases.Lock()
	lifecycle := metadataCatalogLifecycleLocked(databasePath)
	lifecycle.coverageGeneration++
	lifecycle.coverageValid = false
	metadataDatabases.Unlock()
}

func metadataDoScan(internalPath string, drive *orc.Drive) error {
	databasePath := metadataDatabasePath(drive.Id)
	ctx, finishScan, err := metadataBeginScan(databasePath)
	if err != nil {
		return err
	}
	defer finishScan()
	basePath, ok, _ := DriveToLocalPath(drive)
	if !ok {
		return errors.New("unable to resolve metadata scan drive")
	}
	metadataRuntime.Lock()
	limiter := metadataRuntime.limiter
	entriesPerTable := metadataRuntime.config.EntriesPerSSTable
	metadataRuntime.Unlock()
	startedAt := time.Now()
	metadataRecordScanStarted(drive.Id)
	err = metadataScanAndPublish(ctx, internalPath, basePath, databasePath, limiter, entriesPerTable)
	metadataRecordScanFinished(drive.Id, time.Since(startedAt), err)
	aggregatesPublished := err == nil || errors.Is(err, errMetadataNameRepairRequired)
	if aggregatesPublished && shared.ServiceConfig.FileSystem.MetadataCatalog.EnableIntegration {
		metadataReportAccounting(drive)
	}
	if err != nil {
		aggregatesNeedRepair := errors.Is(err, errMetadataAggregateRepairRequired)
		if aggregatesNeedRepair || errors.Is(err, errMetadataNameRepairRequired) {
			metadataInvalidateCatalogScanState(drive.Id)
		}
		if aggregatesNeedRepair && filepath.Clean(internalPath) != filepath.Clean(basePath) {
			go MetadataSubmitScanRequest("/" + drive.Id)
		}
		if !metadataExpectedLiveTreeError(err) && !errors.Is(err, errMetadataScanInvalidated) {
			log.Warn("Metadata scan of drive %s at %s failed: %s", drive.Id, internalPath, err)
		}
	}
	if err == nil && DriveScanListener != nil {
		DriveScanListener(drive, basePath)
	}
	return err
}

func metadataReportAccounting(drive *orc.Drive) {
	stats, found, err := MetadataLookupDirectoryStats("/" + drive.Id)
	if err != nil {
		log.Warn("Unable to retrieve metadata accounting for drive %s: %s", drive.Id, err)
		return
	}
	if !found || !stats.CompleteCoverage {
		return
	}
	size := stats.LogicalBytes
	size = size / uint64(ctrl.ProductCategoryAccountingUnitBytes(drive.Specification.Product.Category))
	reportUsedStorage(*drive, int64(min(size, uint64(math.MaxInt64))))
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

func metadataScanAndPublish(ctx context.Context, scanPath, driveRoot, databasePath string, limiter *rate.Limiter, entriesPerTable int) (scanErr error) {
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
	metadataDatabases.Lock()
	tempDir, err := os.MkdirTemp(databasePath, ".scan-")
	if err == nil {
		metadataDatabases.workspaces[tempDir] = true
	}
	metadataDatabases.Unlock()
	if err != nil {
		return fmt.Errorf("create scan workspace: %w", err)
	}
	defer func() {
		_ = os.RemoveAll(tempDir)
		metadataDatabases.Lock()
		delete(metadataDatabases.workspaces, tempDir)
		metadataDatabases.Unlock()
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

	metadataDatabases.Lock()
	lifecycle := metadataCatalogLifecycleLocked(databasePath)
	metadataDatabases.Unlock()
	lifecycle.publication.Lock()
	defer lifecycle.publication.Unlock()
	metadataInvalidateCoverage(databasePath)
	defer metadataInvalidateCoverage(databasePath)
	if err = ctx.Err(); err != nil {
		return err
	}
	db, releaseDatabase, err := metadataAcquireDatabase(databasePath, true)
	if err != nil {
		return fmt.Errorf("open metadata database: %w", err)
	}
	defer releaseDatabase()

	// NOTE(Dan): The process may have stopped after publishing an earlier PATH replacement but before updating its
	// ancestors or NAME entries. Recover that work before calculating another subtree delta. A root scan replaces the
	// complete PATH keyspace and has no ancestors, so its journal safely supersedes an older pending aggregate update.
	if len(components) != 0 {
		if err = metadataRecoverPendingAggregate(db); err != nil {
			return err
		}
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
		return fmt.Errorf("%w: journal pending aggregate update: %v", errMetadataAggregateRepairRequired, err)
	}
	metadataInvalidateCoverage(databasePath)
	aggregatesPending := true
	defer func() {
		if aggregatesPending && scanErr != nil && !errors.Is(scanErr, errMetadataAggregateRepairRequired) {
			scanErr = fmt.Errorf("%w: %v", errMetadataAggregateRepairRequired, scanErr)
		}
	}()

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
	aggregatesPending = false

	pathBytes, _ := db.EstimateDiskUsage([]byte{MetaKeyspacePath}, []byte{MetaKeyspaceName})
	nameBytes, _ := db.EstimateDiskUsage([]byte{MetaKeyspaceName}, []byte{MetaKeyspaceTrigram + 1})
	metadataRecordScanContents(filepath.Base(databasePath), output.rootEntry, db.Metrics().DiskSpaceUsage(), pathBytes, nameBytes)
	refreshDone := make(chan struct{})
	metadataNameRefreshes.Store(databasePath, refreshDone)
	metadataRecordNameRefreshStarted(filepath.Base(databasePath))
	err = metadataRefreshNameIndexAndFinish(db, databasePath, filepath.Base(databasePath), span, oldNamesPath, scanPath, refreshDone)
	if err != nil {
		return fmt.Errorf("%w: %v", errMetadataNameRepairRequired, err)
	}
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
		children, readErr := directory.Readdirnames(1024)
		for _, child := range children {
			childPath := filepath.Join(path, child)
			childComponents := append(metadataCloneComponents(components), child)
			if err := limiter.Wait(ctx); err != nil {
				return metadataAggregate{}, err
			}
			var childStat unix.Stat_t
			infoErr := unix.Fstatat(int(directory.Fd()), child, &childStat, unix.AT_SYMLINK_NOFOLLOW)
			if infoErr != nil {
				// A file disappearing between ReadDir and Info is normal on a live filesystem. No data for the file has
				// been collected yet, so it is safe to leave it out of this scan without invalidating the subtree.
				if metadataExpectedLiveTreeError(infoErr) {
					continue
				}
				return metadataAggregate{}, fmt.Errorf("stat %q: %w", childPath, infoErr)
			}
			if childStat.Mode&unix.S_IFMT == unix.S_IFDIR {
				// Open directories relative to their parent and do not follow symlinks. A user may replace or rename a
				// directory while it is being scanned. In that case we reject the complete scan instead of publishing
				// records under a path which no longer refers to the directory we visited.
				if err := limiter.Wait(ctx); err != nil {
					return metadataAggregate{}, err
				}
				fd, openErr := unix.Openat(int(directory.Fd()), child, unix.O_RDONLY|unix.O_DIRECTORY|unix.O_NOFOLLOW|unix.O_CLOEXEC, 0)
				if openErr != nil {
					if metadataExpectedLiveTreeError(openErr) {
						return metadataAggregate{}, fmt.Errorf("%w: directory %q changed before opening", errMetadataScanInvalidated, childPath)
					}
					return metadataAggregate{}, fmt.Errorf("open directory %q: %w", childPath, openErr)
				}
				childDirectory := os.NewFile(uintptr(fd), childPath)
				openedInfo, statErr := childDirectory.Stat()
				if statErr != nil || !metadataStatMatchesFileInfo(&childStat, openedInfo) {
					_ = childDirectory.Close()
					return metadataAggregate{}, fmt.Errorf("%w: directory %q changed while opening", errMetadataScanInvalidated, childPath)
				}
				childAggregate, scanErr := metadataScanDirectory(ctx, childPath, childComponents, generation, limiter, output, childDirectory, openedInfo)
				if scanErr != nil {
					_ = childDirectory.Close()
					return metadataAggregate{}, scanErr
				}
				if !metadataDirectoryStillBound(directory, child, openedInfo) {
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

			entry := metadataEntryFromStat(&childStat, generation)
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

func metadataEntryFromStat(stat *unix.Stat_t, generation uint64) MetadataEntry {
	entryType := MetaEntryOther
	mode := os.FileMode(stat.Mode & 0o777)
	switch stat.Mode & unix.S_IFMT {
	case unix.S_IFREG:
		entryType = MetaEntryRegular
	case unix.S_IFDIR:
		entryType = MetaEntryDirectory
		mode |= os.ModeDir
	case unix.S_IFLNK:
		entryType = MetaEntrySymlink
		mode |= os.ModeSymlink
	case unix.S_IFIFO:
		mode |= os.ModeNamedPipe
	case unix.S_IFSOCK:
		mode |= os.ModeSocket
	case unix.S_IFCHR:
		mode |= os.ModeDevice | os.ModeCharDevice
	case unix.S_IFBLK:
		mode |= os.ModeDevice
	}
	if stat.Mode&unix.S_ISUID != 0 {
		mode |= os.ModeSetuid
	}
	if stat.Mode&unix.S_ISGID != 0 {
		mode |= os.ModeSetgid
	}
	if stat.Mode&unix.S_ISVTX != 0 {
		mode |= os.ModeSticky
	}
	return MetadataEntry{
		EntryType:          entryType,
		EntryGeneration:    generation,
		ObservedAtUnixNano: time.Now().UnixNano(),
		LogicalSize:        uint64(max(0, stat.Size)),
		AllocatedSize:      util.OptValue(uint64(max(0, stat.Blocks)) * 512),
		ModificationTime:   stat.Mtim.Sec*int64(time.Second) + stat.Mtim.Nsec,
		AccessTime:         util.OptValue(stat.Atim.Sec*int64(time.Second) + stat.Atim.Nsec),
		DeviceID:           util.OptValue(uint64(stat.Dev)),
		InodeID:            util.OptValue(stat.Ino),
		Mode:               util.OptValue(uint64(mode)),
		LinkCount:          util.OptValue(uint64(stat.Nlink)),
	}
}

func metadataStatMatchesFileInfo(stat *unix.Stat_t, info os.FileInfo) bool {
	expected, ok := info.Sys().(*syscall.Stat_t)
	return ok && uint64(stat.Dev) == uint64(expected.Dev) && stat.Ino == expected.Ino
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
	absoluteRoot, err := filepath.Abs(driveRoot)
	if err != nil {
		return nil, nil, err
	}
	rootComponents := metadataRelativeComponents(strings.TrimPrefix(absoluteRoot, string(filepath.Separator)))
	allComponents := append(rootComponents, components...)
	if err := limiter.Wait(ctx); err != nil {
		return nil, nil, err
	}
	fd, err := unix.Open("/", unix.O_RDONLY|unix.O_DIRECTORY|unix.O_NOFOLLOW|unix.O_CLOEXEC, 0)
	if err != nil {
		return nil, nil, fmt.Errorf("open drive root %q: %w", driveRoot, err)
	}
	directory := os.NewFile(uintptr(fd), "/")
	for _, component := range allComponents {
		if err = limiter.Wait(ctx); err != nil {
			_ = directory.Close()
			return nil, nil, err
		}
		fd, err = unix.Openat(int(directory.Fd()), component, unix.O_RDONLY|unix.O_DIRECTORY|unix.O_NOFOLLOW|unix.O_CLOEXEC, 0)
		if err != nil {
			_ = directory.Close()
			return nil, nil, fmt.Errorf("open scan root %q: %w", filepath.Join(driveRoot, filepath.Join(components...)), err)
		}
		next := os.NewFile(uintptr(fd), filepath.Join(directory.Name(), component))
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

func metadataTrigramKeys(pathKey []byte) ([][]byte, error) {
	components, err := metadataPathComponents(pathKey)
	if err != nil || len(components) == 0 {
		return nil, err
	}
	normalized := metadataNormalizedName([]byte(components[len(components)-1]))
	trigrams := metadataNameTrigrams(normalized)
	keys := make([][]byte, 0, len(trigrams))
	for _, trigram := range trigrams {
		key := []byte{MetaKeyspaceTrigram}
		key = append(key, trigram...)
		key = append(key, 0)
		key = append(key, pathKey[1:]...)
		keys = append(keys, key)
	}
	return keys, nil
}

func metadataNameTrigrams(normalized []byte) [][]byte {
	if !utf8.Valid(normalized) {
		if len(normalized) < 3 {
			return nil
		}
		seen := map[string]bool{}
		result := make([][]byte, 0, len(normalized)-2)
		for i := 0; i+3 <= len(normalized); i++ {
			trigram := normalized[i : i+3]
			if !seen[string(trigram)] {
				seen[string(trigram)] = true
				result = append(result, append([]byte(nil), trigram...))
			}
		}
		return result
	}

	runes := []rune(string(normalized))
	if len(runes) < 3 {
		return nil
	}
	seen := map[string]bool{}
	result := make([][]byte, 0, len(runes)-2)
	for i := 0; i+3 <= len(runes); i++ {
		trigram := string(runes[i : i+3])
		if !seen[trigram] {
			seen[trigram] = true
			result = append(result, []byte(trigram))
		}
	}
	return result
}

func metadataSearchIndexKeys(pathKey []byte) ([][]byte, error) {
	nameKey, err := metadataNameKey(pathKey)
	if err != nil || nameKey == nil {
		return nil, err
	}
	trigramKeys, err := metadataTrigramKeys(pathKey)
	if err != nil {
		return nil, err
	}
	return append([][]byte{nameKey}, trigramKeys...), nil
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
	lifecycle := metadataCatalogLifecycleLocked(databasePath)
	if lifecycle.deleted {
		metadataDatabases.Unlock()
		return nil, func() {}, errMetadataCatalogDeleted
	}
	lifecycle.databaseUsers++
	metadataDatabases.Unlock()
	lifecycle.databaseMutex.Lock()
	if lifecycle.database != nil {
		lifecycle.databaseRefs++
		db := lifecycle.database
		lifecycle.databaseMutex.Unlock()
		return db, metadataDatabaseRelease(databasePath, lifecycle), nil
	}
	if !create {
		_, err := os.Stat(databasePath)
		if errors.Is(err, os.ErrNotExist) {
			lifecycle.databaseMutex.Unlock()
			metadataDatabaseUserFinished(databasePath, lifecycle)
			return nil, func() {}, errMetadataCatalogNotFound
		}
		if err != nil {
			lifecycle.databaseMutex.Unlock()
			metadataDatabaseUserFinished(databasePath, lifecycle)
			return nil, func() {}, err
		}
	}
	opts := &pebble.Options{FormatMajorVersion: pebble.FormatNewest}
	opts.ApplyCompressionSettings(func() pebble.DBCompressionSettings {
		return pebble.DBCompressionBalanced
	})
	db, err := pebble.Open(databasePath, opts)
	if err != nil {
		lifecycle.databaseMutex.Unlock()
		metadataDatabaseUserFinished(databasePath, lifecycle)
		return nil, func() {}, err
	}
	lifecycle.database = db
	lifecycle.databaseRefs = 1
	lifecycle.databaseMutex.Unlock()
	return db, metadataDatabaseRelease(databasePath, lifecycle), nil
}

func metadataDatabaseRelease(databasePath string, lifecycle *metadataCatalogLifecycle) func() {
	return func() {
		lifecycle.databaseMutex.Lock()
		lifecycle.databaseRefs--
		if lifecycle.databaseRefs == 0 {
			db := lifecycle.database
			lifecycle.database = nil
			_ = db.Close()
		}
		lifecycle.databaseMutex.Unlock()
		metadataDatabaseUserFinished(databasePath, lifecycle)
	}
}

func metadataDatabaseUserFinished(databasePath string, lifecycle *metadataCatalogLifecycle) {
	metadataDatabases.Lock()
	lifecycle.databaseUsers--
	metadataCatalogSignalDrainedLocked(databasePath)
	metadataDatabases.Unlock()
}

func metadataCatalogLifecycleLocked(databasePath string) *metadataCatalogLifecycle {
	lifecycle := metadataDatabases.catalogs[databasePath]
	if lifecycle == nil {
		lifecycle = &metadataCatalogLifecycle{scans: map[uint64]context.CancelFunc{}}
		metadataDatabases.catalogs[databasePath] = lifecycle
	}
	return lifecycle
}

func metadataCatalogSignalDrainedLocked(databasePath string) {
	lifecycle := metadataDatabases.catalogs[databasePath]
	if lifecycle.deleted && lifecycle.drained != nil && len(lifecycle.scans) == 0 && lifecycle.databaseUsers == 0 {
		close(lifecycle.drained)
		lifecycle.drained = nil
	}
}

func metadataBeginScan(databasePath string) (context.Context, func(), error) {
	metadataDatabases.Lock()
	lifecycle := metadataCatalogLifecycleLocked(databasePath)
	if lifecycle.deleted {
		metadataDatabases.Unlock()
		return nil, func() {}, errMetadataCatalogDeleted
	}
	ctx, cancel := context.WithCancel(context.Background())
	lifecycle.nextScan++
	scanId := lifecycle.nextScan
	lifecycle.scans[scanId] = cancel
	metadataDatabases.Unlock()
	return ctx, func() {
		cancel()
		metadataDatabases.Lock()
		delete(lifecycle.scans, scanId)
		metadataCatalogSignalDrainedLocked(databasePath)
		metadataDatabases.Unlock()
	}, nil
}

func metadataCatalogRetired(driveId string) bool {
	databasePath := metadataDatabasePath(driveId)
	metadataDatabases.Lock()
	lifecycle := metadataDatabases.catalogs[databasePath]
	retired := lifecycle != nil && lifecycle.deleted
	metadataDatabases.Unlock()
	return retired
}

func metadataRetireCatalog(driveId string) (<-chan struct{}, bool) {
	drained, retired := metadataRetireCatalogState(driveId)
	if retired {
		go func() {
			<-drained
			metadataStageCatalogDeletion(driveId)
		}()
	}
	return drained, retired
}

func metadataRetireCatalogState(driveId string) (<-chan struct{}, bool) {
	databasePath := metadataDatabasePath(driveId)
	metadataDatabases.Lock()
	lifecycle := metadataCatalogLifecycleLocked(databasePath)
	if lifecycle.deleted {
		drained := lifecycle.drained
		metadataDatabases.Unlock()
		if drained == nil {
			drained = make(chan struct{})
			close(drained)
		}
		return drained, false
	}
	lifecycle.deleted = true
	lifecycle.drained = make(chan struct{})
	drained := lifecycle.drained
	for _, cancel := range lifecycle.scans {
		cancel()
	}
	metadataCatalogSignalDrainedLocked(databasePath)
	metadataDatabases.Unlock()
	return drained, true
}

func metadataStageCatalogDeletion(driveId string) {
	databasePath := metadataDatabasePath(driveId)
	device, inode, err := metadataDeletionPathIdentity(databasePath)
	if err != nil {
		log.Warn("Unable to inspect metadata catalog for drive %s: %s", driveId, err)
		return
	}
	stagingName := "metadata-" + filepath.Base(databasePath) + "-" + util.RandomToken(8)
	err = metadataStageDeletionPath(databasePath, stagingName, device, inode)
	if err != nil {
		log.Warn("Unable to stage metadata catalog for drive %s: %s", driveId, err)
	}
}

func metadataReadEntry(reader pebble.Reader, key []byte) (MetadataEntry, bool, error) {
	value, closer, err := reader.Get(key)
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
	if len(scanComponents) == 0 {
		err := batch.Set(metadataAggregateVersionKey, metadataAggregateVersion, nil)
		if err != nil {
			return err
		}
	}
	for depth := len(scanComponents) - 1; depth >= 0; depth-- {
		key := metadataPathKey(scanComponents[:depth])
		ancestor, present, err := metadataReadEntry(db, key)
		if err != nil {
			return err
		}
		if !present || ancestor.EntryType != MetaEntryDirectory {
			return fmt.Errorf("%w: missing directory ancestor %x", errMetadataAggregateRepairRequired, key)
		}
		if err = metadataApplyAggregateDelta(&ancestor, oldContribution, newContribution); err != nil {
			return fmt.Errorf("%w: update ancestor PATH key %x: %v", errMetadataAggregateRepairRequired, key, err)
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
