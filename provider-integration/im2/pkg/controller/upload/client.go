package upload

import (
	"bytes"
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"os"
	"runtime"
	"runtime/pprof"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	ws "github.com/gorilla/websocket"
	"golang.org/x/sync/semaphore"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const chunkIoDeadline = 30 * time.Second
const chanBufferSize = 1024 * 32

type ClientSession struct {
	Endpoint       string
	ConflictPolicy orc.WriteConflictPolicy
	Path           string
	Metrics        ClientMetrics
}

type ClientMetrics struct {
	lock             sync.Mutex
	BytesTransferred int64
	FilesCompleted   int64
	FilesSkipped     int64
	SkipReasons      map[string]SkipReason
}

func (m *ClientMetrics) Absorb(other *ClientMetrics) {
	other.lock.Lock()
	defer other.lock.Unlock()

	m.lock.Lock()
	defer m.lock.Unlock()

	m.FilesCompleted += other.FilesCompleted
	m.BytesTransferred += other.BytesTransferred
	m.FilesSkipped += other.FilesSkipped

	for key, reasons := range other.SkipReasons {
		myReasons := m.SkipReasons[key]
		initialCount := myReasons.Count
		for i := 0; i < len(reasons.Paths); i++ {
			myReasons.AddSkip(reasons.Reasons[i], reasons.Paths[i])
		}
		myReasons.Count = initialCount + reasons.Count
		m.SkipReasons[key] = myReasons
	}
}

func (m *ClientMetrics) CompleteFile(size int64) {
	m.lock.Lock()
	defer m.lock.Unlock()

	m.BytesTransferred += size
	m.FilesCompleted++
}

func (m *ClientMetrics) SkipFile(reason string, path string) {
	m.lock.Lock()
	defer m.lock.Unlock()

	m.FilesSkipped++
	truncatedReason := reason[:min(len(reason), 16)]
	skips, _ := m.SkipReasons[truncatedReason]
	skips.AddSkip(reason, path)
	m.SkipReasons[reason] = skips
}

type SkipReason struct {
	Count   int
	Reasons []string
	Paths   []string // Will not store more than 16 example paths
}

func (s *SkipReason) AddSkip(reason, path string) {
	s.Count++
	if len(s.Paths) < 16 {
		s.Paths = append(s.Paths, path)
		s.Reasons = append(s.Reasons, reason)
	}
}

type ClientFile interface {
	ListChildren(ctx context.Context) []string
	OpenChild(ctx context.Context, name string) (FileMetadata, ClientFile)
	Read(ctx context.Context, target []byte) (int, bool, error)
	Close()
}

type clientWork struct {
	Id         int32
	File       ClientFile
	Metadata   FileMetadata
	WorkType   int
	Done       atomic.Bool
	AssignedTo int
}

type clientWorker struct {
	WorkerId         int
	Ctx              context.Context
	OpenWork         chan *clientWork
	AssignedWork     chan *clientWork
	Connection       *ws.Conn
	FilesCompleted   atomic.Int32
	BytesTransferred atomic.Int64
	Metrics          ClientMetrics
}

const (
	minChunkSize = 1024 * 16
	maxChunkSize = 1024 * 1024 * 4
)

func (w *clientWorker) Process() {
	rawChunkBytes1 := make([]byte, maxChunkSize)
	rawChunkBytes2 := make([]byte, maxChunkSize)
	recommendedChunkSize := atomic.Int64{}
	recommendedChunkSize.Store(minChunkSize)

	skipBuffer := util.NewBuffer(&bytes.Buffer{})
	listingBuffer := util.NewBuffer(&bytes.Buffer{})
	listingTicker := time.NewTicker(50 * time.Millisecond)

	combinedTaskQueue := make(chan *clientWork, chanBufferSize)

	go func() {
	loop:
		for {
			select {
			case <-w.Ctx.Done():
				break loop

			case work, ok := <-w.AssignedWork:
				if ok {
					combinedTaskQueue <- work
				}

			case work, ok := <-w.OpenWork:
				if ok {
					combinedTaskQueue <- work
				}
			}
		}
	}()

	nextPing := time.Now()
	pingFailures := 0

	attemptToSendPing := func() bool {
		now := time.Now()
		if now.After(nextPing) {
			err := w.Connection.WriteMessage(ws.TextMessage, []byte("ping"))
			if err != nil {
				// TODO(Dan): This shouldn't be needed, but it seems like we often fail a ping right as the
				//   connection is shutting down and this is causing some issues with the entire transfer.
				pingFailures++
				if pingFailures >= 30 {
					_ = w.Connection.Close()
					log.Warn("Uploader client failed to send ping: %v", err)
					return false
				}
			}

			nextPing = nextPing.Add(chunkIoDeadline / 2)
		}
		return true
	}

outer:
	for {
		select {
		case <-w.Ctx.Done():
			break outer

		case <-listingTicker.C:
			if !attemptToSendPing() {
				break outer
			}

			listingMessage := listingBuffer.ReadRemainingBytes()
			if len(listingMessage) > 0 {
				err1 := w.Connection.SetWriteDeadline(time.Now().Add(chunkIoDeadline))
				err2 := w.Connection.WriteMessage(ws.BinaryMessage, listingMessage)
				if err := util.FindError(err1, err2); err != nil {
					_ = w.Connection.Close()
					log.Warn("Upload client failed to send message: %v", err)
					break outer
				}

				listingBuffer.Reset()
			}

		case work := <-combinedTaskQueue:
			if !attemptToSendPing() {
				break outer
			}

			switch work.WorkType {
			case 0:
				// Send a listing
				(&messageListing{
					FileId:     work.Id,
					Size:       work.Metadata.Size,
					ModifiedAt: work.Metadata.ModifiedAt.UnixMilli(),
					Path:       work.Metadata.InternalPath,
				}).Marshal(listingBuffer, listingBuffer.IsEmpty())

				work.AssignedTo = w.WorkerId

			case 1:
				// Send chunks
				written := int64(0)

				readBuffersAvailable := semaphore.NewWeighted(2)
				writeBuffers := make(chan []byte, 2)

				go func() {
					size := recommendedChunkSize.Load()
					chunkBytes1 := rawChunkBytes1[:size]
					chunkBytes2 := rawChunkBytes2[:size]
					bufIdx := uint64(0)

					chunkBytes := chunkBytes1
					for {
						// Acquire read permit (in-case writing is too slow, it usually is)
						err := readBuffersAvailable.Acquire(w.Ctx, 1)
						if err != nil {
							break
						}

						chunkBytes[0] = uint8(messageTypeChunk)
						binary.BigEndian.PutUint32(chunkBytes[1:], uint32(work.Id))
						count, isDone, err := work.File.Read(w.Ctx, chunkBytes[5:])
						if count > 0 {
							written += int64(count)
							w.BytesTransferred.Add(int64(count))

							data := chunkBytes[:5+count]
							writeBuffers <- data
						}

						if isDone {
							if err != nil {
								if errors.Is(err, io.EOF) {
									w.Metrics.SkipFile(err.Error(), work.Metadata.InternalPath)
								}
							}
							writeBuffers <- nil
							break
						}

						// Reload size recommendation
						oldSize := size
						size = recommendedChunkSize.Load()
						if size != oldSize {
							log.Info("New size! %v -> %v", oldSize, size)
						}
						chunkBytes1 = rawChunkBytes1[:size]
						chunkBytes2 = rawChunkBytes2[:size]

						// Swap buffers
						bufIdx++
						if bufIdx%2 == 0 {
							chunkBytes = chunkBytes1
						} else {
							chunkBytes = chunkBytes2
						}
					}
				}()

				chunkStartTime := time.Now()
				for {
					data := <-writeBuffers
					if data == nil {
						if work.Metadata.Size != written {
							skipBuffer.Reset()
							(&messageSkip{FileId: work.Id}).Marshal(skipBuffer, true)

							err1 := w.Connection.SetWriteDeadline(time.Now().Add(chunkIoDeadline))
							err2 := w.Connection.WriteMessage(ws.BinaryMessage, skipBuffer.ReadRemainingBytes())
							if err := util.FindError(err1, err2); err != nil {
								_ = w.Connection.Close()
								log.Warn("Uploader client failed to send message: %v", err)
								break outer
							}
						}

						break
					}

					if !attemptToSendPing() {
						break outer
					}

					err1 := w.Connection.SetWriteDeadline(time.Now().Add(chunkIoDeadline))
					err2 := w.Connection.WriteMessage(ws.BinaryMessage, data)
					if err := util.FindError(err1, err2); err != nil {
						_ = w.Connection.Close()
						log.Warn("Uploader client failed to send message: %v", err)
						break outer
					}

					chunkEndTime := time.Now()
					if chunkEndTime.Sub(chunkStartTime) < 250*time.Millisecond && len(data) > 0 {
						newSize := int64(len(data) * 2)
						newSize = min(maxChunkSize, max(minChunkSize, newSize))
						recommendedChunkSize.Store(newSize)
					}

					readBuffersAvailable.Release(1)
				}

				work.Done.Swap(true)
				w.FilesCompleted.Add(1)
				work.File.Close()
				w.Metrics.CompleteFile(work.Metadata.Size)

			case 2:
				// Skip the file
				work.Done.Swap(true)
				w.FilesCompleted.Add(1)
				work.File.Close()
				w.Metrics.SkipFile("server skip", work.Metadata.InternalPath)
			}
		}
	}
}

func goDiscoverClientWork(ctx context.Context, root ClientFile, rootMetadata FileMetadata, output chan *clientWork) {
	idAcc := int32(0)
	stack := []ClientFile{}

	if rootMetadata.Type == FileTypeFile {
		work := &clientWork{
			Id:       idAcc,
			File:     root,
			Metadata: rootMetadata,
		}
		select {
		case <-ctx.Done():
			// All good
		case output <- work:
			// All good
		}
		idAcc++
	} else {
		stack = append(stack, root)
	}

outer:
	for len(stack) > 0 {
		last := len(stack) - 1
		dir := stack[last]
		stack = stack[:last]

		children := dir.ListChildren(ctx)
		for _, child := range children {
			meta, file := dir.OpenChild(ctx, child)
			if file == nil {
				continue
			}

			if meta.Type == FileTypeDirectory {
				stack = append(stack, file)
			} else {
				work := &clientWork{
					Id:       idAcc,
					File:     file,
					Metadata: meta,
				}
				idAcc++

				select {
				case <-ctx.Done():
					break outer
				case output <- work:
					// All good
				}
			}
		}

		dir.Close()
	}

	// In case we are breaking due to an error, close all files we currently have open
	for _, file := range stack {
		file.Close()
	}

	output <- nil
}

type StatusReport struct {
	// NewFilesUploaded represents the actual amount of files going onto the wire. The files are not guaranteed to be
	// completely uploaded.
	NewFilesUploaded int64

	// TotalFilesProcessed represents the amount of files coming from the source directory. These might be skipped if
	// the file is determined to already be present at the destination.
	TotalFilesProcessed int64

	// BytesTransferred represents the actual amount of bytes going onto the wire.
	BytesTransferred int64

	// TotalBytesProcessed represents the amount of bytes coming from the source files. These might be skipped if the
	// file is determined to already be present at the destination.
	TotalBytesProcessed int64

	NormalExit bool

	WasCancelledByUser bool
}

func ProcessClient(
	session ClientSession,
	rootFile ClientFile,
	rootMetadata FileMetadata,
	status *atomic.Pointer[fnd.TaskStatus],
	requestCancel chan util.Empty,
) StatusReport {
	initialStatus := fnd.TaskStatus{}
	if status != nil {
		ptr := status.Load()
		if ptr != nil {
			initialStatus = *ptr
		}
	}

	const numberOfConnections = 16
	const enableProfiling = false

	// Profiling
	// -----------------------------------------------------------------------------------------------------------------
	profileName := "u-" + time.Now().Format(time.TimeOnly)

	var cpuProfile *os.File
	var memProfile *os.File
	if enableProfiling {
		cpu, err1 := os.Create("/tmp/" + profileName + ".cpu")
		mem, err2 := os.Create("/tmp/" + profileName + ".mem")

		cpuProfile = cpu
		memProfile = mem

		err3 := pprof.StartCPUProfile(cpuProfile)

		if err1 != nil || err2 != nil || err3 != nil {
			panic("could not start profiling")
		}
	}

	// State
	// -----------------------------------------------------------------------------------------------------------------
	type incomingMessage struct {
		Sender  int
		Message []byte
	}

	var connections []*ws.Conn
	var workers []*clientWorker
	activeWork := map[int32]*clientWork{}

	sessionContext, cancel := context.WithCancel(context.Background())

	statTicker := time.NewTicker(500 * time.Millisecond)
	discoveredWorkForUs := make(chan *clientWork, chanBufferSize)
	discoveredWorkForWorkers := make(chan *clientWork, chanBufferSize)
	incomingWebsocket := make(chan incomingMessage)

	// Stats
	// -----------------------------------------------------------------------------------------------------------------
	bytesDiscovered := int64(0)
	filesDiscovered := int64(0)
	filesDiscoveredDone := false
	var filesCompletedFromServer []int32
	filesBeingSentOnWire := int64(0)

	filesPerSecond := float64(0)
	bytesPerSecond := float64(0)

	lastFilesCompleted := int32(0)
	lastBytesTransferred := int64(0)

	lastStatMeasurement := time.Now()

	wasCancelledByUser := false

	// Immediately set a state in case of connection failure below
	// -----------------------------------------------------------------------------------------------------------------
	if status != nil {
		newStatus := &fnd.TaskStatus{
			State:              fnd.TaskStateRunning,
			Body:               util.OptValue(""),
			Progress:           util.OptValue("Attempting to establish connection..."),
			ProgressPercentage: util.OptValue(-1.0),
		}
		status.Store(newStatus)
	}

	// Spawn workers (WebSockets and discovery)
	// -----------------------------------------------------------------------------------------------------------------
	activeWorkerCount := atomic.Int32{}
	for i := 0; i < numberOfConnections; i++ {
		whoami := i
		socket, _, err := ws.DefaultDialer.Dial(session.Endpoint, nil)
		if err != nil {
			log.Warn("[%v] Failed to establish WebSocket connection: %v %v", whoami, session.Endpoint, err)
			cancel()
			break
		} else {
			connections = append(connections, socket)
			activeWorkerCount.Add(1)

			go func() {
				for {
					err := socket.SetReadDeadline(time.Now().Add(chunkIoDeadline * 4))
					wsType, data, readErr := socket.ReadMessage()
					if wsType == ws.TextMessage {
						continue
					}

					if wsType != ws.BinaryMessage || readErr != nil || err != nil {
						break
					}

					incomingWebsocket <- incomingMessage{
						Sender:  whoami,
						Message: data,
					}
				}

				activeWorkerCount.Add(-1)
				if !filesDiscoveredDone || activeWorkerCount.Load() == 0 {
					// NOTE(Dan): This is not always a critical failure since it might just have been closed due to
					// _this_ connection no longer having any work.
					cancel()
				}
			}()

			worker := &clientWorker{
				WorkerId:     whoami,
				Ctx:          sessionContext,
				OpenWork:     discoveredWorkForWorkers,
				AssignedWork: make(chan *clientWork, chanBufferSize),
				Connection:   socket,
				Metrics: ClientMetrics{
					SkipReasons: make(map[string]SkipReason),
				},
			}
			workers = append(workers, worker)
			filesCompletedFromServer = append(filesCompletedFromServer, 0)

			go func() {
				worker.Process()
				cancel()
			}()
		}
	}

	go goDiscoverClientWork(sessionContext, rootFile, rootMetadata, discoveredWorkForUs)

	flushStats := func() {
		now := time.Now()
		dt := now.Sub(lastStatMeasurement)
		lastStatMeasurement = now

		bytesTransferred := int64(0)
		filesCompleted := int32(0)
		for _, w := range workers {
			filesCompleted += w.FilesCompleted.Load()
			bytesTransferred += w.BytesTransferred.Load()
		}

		filesPerSecond = util.SmoothMeasure(filesPerSecond, float64(filesCompleted-lastFilesCompleted)/dt.Seconds())

		f := float64(bytesTransferred - lastBytesTransferred)
		seconds := dt.Seconds()
		bytesPerSecond = util.SmoothMeasure(bytesPerSecond, f/seconds)

		lastFilesCompleted = filesCompleted
		lastBytesTransferred = bytesTransferred

		totalFilesCompletedServer := int32(0)
		for _, f := range filesCompletedFromServer {
			totalFilesCompletedServer += f
		}

		readableSpeed := util.SizeToHumanReadableWithUnit(bytesPerSecond)

		if status != nil {
			percentage := (float64(bytesTransferred) / float64(bytesDiscovered)) * 100
			if !filesDiscoveredDone {
				percentage = -1
			}

			readableDataTransferred := util.SizeToHumanReadableWithUnit(float64(bytesTransferred))
			readableDiscoveredDataSize := util.SizeToHumanReadableWithUnit(float64(bytesDiscovered))

			newStatus := &fnd.TaskStatus{
				State: fnd.TaskStateRunning,
				Title: initialStatus.Title,
				Body: util.OptValue(fmt.Sprintf(
					"%.2f %v/%.2f %v | %v / %v files",
					readableDataTransferred.Size,
					readableDataTransferred.Unit,
					readableDiscoveredDataSize.Size,
					readableDiscoveredDataSize.Unit,
					filesCompleted,
					filesDiscovered,
				)),
				Progress: util.OptValue(fmt.Sprintf(
					"%.2f %v/s | %.2f files/s",
					readableSpeed.Size,
					readableSpeed.Unit,
					filesPerSecond,
				)),
				ProgressPercentage: util.OptValue(percentage),
			}
			status.Store(newStatus)
		}
	}

	// Process input from workers
	// -----------------------------------------------------------------------------------------------------------------

outer:
	for {
		select {
		case <-requestCancel:
			wasCancelledByUser = true
			break outer

		case <-sessionContext.Done():
			break outer

		case <-statTicker.C:
			flushStats()

		case work := <-discoveredWorkForUs:
			if work == nil {
				filesDiscoveredDone = true
			} else {
				activeWork[work.Id] = work
				select {
				case discoveredWorkForWorkers <- work:
				case <-sessionContext.Done():
					break outer
				}
				filesDiscovered++
				bytesDiscovered += work.Metadata.Size
			}

		case wsMsg, ok := <-incomingWebsocket:
			if !ok {
				break outer
			}

			buf := util.NewBuffer(bytes.NewBuffer(wsMsg.Message))
			for !buf.IsEmpty() {
				t := messageType(buf.ReadU8())
				rawMessage := parseMessage(t, buf)
				if rawMessage == nil {
					break
				}

				switch msg := rawMessage.(type) {
				case *messageCompleted:
					filesCompletedFromServer[wsMsg.Sender] = msg.NumberOfProcessedFiles

					totalFilesCompleted := int32(0)
					for _, f := range filesCompletedFromServer {
						totalFilesCompleted += f
					}
					if filesDiscoveredDone && int32(filesDiscovered) == totalFilesCompleted {
						break outer
					}

				case *messageSkip:
					work, ok := activeWork[msg.FileId]
					if !ok {
						log.Warn("Was told about some work which is no longer active: %v", msg.FileId)
						break outer
					}
					delete(activeWork, msg.FileId)

					work.WorkType = 2

					select {
					case workers[work.AssignedTo].AssignedWork <- work:
					case <-sessionContext.Done():
						break outer
					}

				case *messageOk:
					work, ok := activeWork[msg.FileId]
					if !ok {
						log.Warn("Was told about some work which is no longer active: %v", msg.FileId)
						break outer
					}
					delete(activeWork, msg.FileId)

					filesBeingSentOnWire++
					work.WorkType = 1

					select {
					case workers[work.AssignedTo].AssignedWork <- work:
					case <-sessionContext.Done():
						break outer
					}

				}
			}
		}
	}

	// Cleanup
	// -----------------------------------------------------------------------------------------------------------------

	cancel()

	for _, c := range connections {
		_ = c.Close()
	}

	if memProfile != nil && cpuProfile != nil {
		runtime.GC()
		_ = pprof.WriteHeapProfile(memProfile)
		_ = memProfile.Close()

		pprof.StopCPUProfile()
		_ = cpuProfile.Close()
	}

	totalFilesCompleted := int64(0)
	for _, f := range filesCompletedFromServer {
		totalFilesCompleted += int64(f)
	}

	combined := ClientMetrics{
		SkipReasons: make(map[string]SkipReason),
	}
	for _, w := range workers {
		if w != nil {
			combined.Absorb(&w.Metrics)
		}
	}

	if combined.FilesCompleted != 0 || combined.FilesSkipped != 0 || combined.BytesTransferred != 0 {
		b := strings.Builder{}
		b.WriteString(fmt.Sprintf(
			"Upload session complete (client): %v file(s) transferred | %v bytes transferred | %v file(s) skipped\n",
			combined.FilesCompleted,
			combined.BytesTransferred,
			combined.FilesSkipped,
		))
		for trunc, f := range combined.SkipReasons {
			b.WriteString("\t")
			b.WriteString(trunc)
			b.WriteString(":\n")
			b.WriteString(fmt.Sprintf("\t\tCount: %v\n", f.Count))
			for i := 0; i < len(f.Paths); i++ {
				b.WriteString(fmt.Sprintf("\t\t%v: %v\n", f.Reasons[i], f.Paths[i]))
			}
		}

		output := b.String()
		log.Info(output)
	}

	return StatusReport{
		NewFilesUploaded:    filesBeingSentOnWire,
		TotalFilesProcessed: filesDiscovered,
		BytesTransferred:    lastBytesTransferred,
		TotalBytesProcessed: bytesDiscovered,
		NormalExit:          filesDiscoveredDone && filesDiscovered == totalFilesCompleted,
		WasCancelledByUser:  wasCancelledByUser,
	}
}

func CloseSessionFromClient(session ClientSession) bool {
	socket, _, err := ws.DefaultDialer.Dial(session.Endpoint, nil)
	if err != nil {
		log.Warn("Failed to establish WebSocket connection: %v %v", session.Endpoint, err)
		return false
	}

	messageBuffer := util.NewBuffer(&bytes.Buffer{})
	(&messageClose{}).Marshal(messageBuffer, messageBuffer.IsEmpty())

	err1 := socket.SetWriteDeadline(time.Now().Add(1 * time.Minute))
	err2 := socket.WriteMessage(ws.BinaryMessage, messageBuffer.ReadRemainingBytes())
	if err := util.FindError(err1, err2); err != nil {
		log.Warn("Failed to send closure message to uploader: %v", err)
		return false
	}

	return true
}
