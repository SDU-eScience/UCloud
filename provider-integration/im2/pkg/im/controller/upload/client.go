package upload

import (
	"bytes"
	"context"
	"encoding/binary"
	"fmt"
	ws "github.com/gorilla/websocket"
	"golang.org/x/sync/semaphore"
	"os"
	"runtime"
	"runtime/pprof"
	"sync/atomic"
	"time"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

const chunkIoDeadline = 30 * time.Second

type ClientSession struct {
	Endpoint       string
	ConflictPolicy orc.WriteConflictPolicy
	Path           string
}

type ClientFile interface {
	ListChildren(ctx context.Context) []string
	OpenChild(ctx context.Context, name string) (FileMetadata, ClientFile)
	Read(ctx context.Context, target []byte) (int, bool)
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
}

func (w *clientWorker) Process() {
	chunkBytes1 := make([]byte, 1024*1024*16)
	chunkBytes2 := make([]byte, 1024*1024*16)

	skipBuffer := util.NewBuffer(&bytes.Buffer{})
	listingBuffer := util.NewBuffer(&bytes.Buffer{})
	listingTicker := time.NewTicker(50 * time.Millisecond)

	combinedTaskQueue := make(chan *clientWork)

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
				if pingFailures >= 10 {
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
					chunkBytes := chunkBytes1
					nextBuf := chunkBytes2
					for {
						// Acquire read permit (in-case writing is too slow, it usually is)
						err := readBuffersAvailable.Acquire(w.Ctx, 1)
						if err != nil {
							break
						}

						chunkBytes[0] = uint8(messageTypeChunk)
						binary.BigEndian.PutUint32(chunkBytes[1:], uint32(work.Id))
						count, isDone := work.File.Read(w.Ctx, chunkBytes[5:])
						if count > 0 {
							written += int64(count)
							w.BytesTransferred.Add(int64(count))

							data := chunkBytes[:5+count]
							writeBuffers <- data
						}

						if isDone {
							writeBuffers <- nil
							break
						}

						// Swap buffers
						tmp := chunkBytes
						chunkBytes = nextBuf
						nextBuf = tmp
					}
				}()

				for {
					data := <-writeBuffers
					if data == nil {
						if work.Metadata.Size != written {
							skipBuffer.Reset()
							(&messageSkip{FileId: work.Id}).Marshal(skipBuffer, true)

							err1 := w.Connection.SetWriteDeadline(time.Now().Add(chunkIoDeadline))
							err2 := w.Connection.WriteMessage(ws.BinaryMessage, data)
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

					readBuffersAvailable.Release(1)
				}

				work.Done.Swap(true)
				w.FilesCompleted.Add(1)
				work.File.Close()

			case 2:
				// Skip the file
				work.Done.Swap(true)
				w.FilesCompleted.Add(1)
				work.File.Close()
			}
		}
	}
}

func goDiscoverClientWork(ctx context.Context, root ClientFile, output chan *clientWork) {
	idAcc := int32(0)
	stack := []ClientFile{root}

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
	status *atomic.Pointer[orc.TaskStatus],
	requestCancel chan util.Empty,
) StatusReport {
	initialStatus := orc.TaskStatus{}
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
	discoveredWorkForUs := make(chan *clientWork)
	discoveredWorkForWorkers := make(chan *clientWork)
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
		newStatus := &orc.TaskStatus{
			State:              orc.TaskStateRunning,
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
				AssignedWork: make(chan *clientWork),
				Connection:   socket,
			}
			workers = append(workers, worker)
			filesCompletedFromServer = append(filesCompletedFromServer, 0)

			go func() {
				worker.Process()
				cancel()
			}()
		}
	}

	go goDiscoverClientWork(sessionContext, rootFile, discoveredWorkForUs)

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

		filesPerSecond = smoothMeasure(filesPerSecond, float64(filesCompleted-lastFilesCompleted)/dt.Seconds())

		f := float64(bytesTransferred - lastBytesTransferred)
		seconds := dt.Seconds()
		bytesPerSecond = smoothMeasure(bytesPerSecond, f/seconds)

		lastFilesCompleted = filesCompleted
		lastBytesTransferred = bytesTransferred

		totalFilesCompletedServer := int32(0)
		for _, f := range filesCompletedFromServer {
			totalFilesCompletedServer += f
		}

		readableSpeed := sizeToHumanReadableWithUnit(bytesPerSecond)

		if status != nil {
			percentage := (float64(bytesTransferred) / float64(bytesDiscovered)) * 100
			if !filesDiscoveredDone {
				percentage = -1
			}

			readableDataTransferred := sizeToHumanReadableWithUnit(float64(bytesTransferred))
			readableDiscoveredDataSize := sizeToHumanReadableWithUnit(float64(bytesDiscovered))

			newStatus := &orc.TaskStatus{
				State: orc.TaskStateRunning,
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

type readableSize struct {
	Size float64
	Unit string
}

func sizeToHumanReadableWithUnit(bytes float64) readableSize {
	if bytes < 1000 {
		return readableSize{
			Size: bytes, Unit: "B",
		}
	} else if bytes < 1000*1000 {
		return readableSize{
			Size: bytes / 1000,
			Unit: "KB",
		}
	} else if bytes < 1000*1000*1000 {
		return readableSize{
			Size: bytes / (1000 * 1000),
			Unit: "MB",
		}
	} else if bytes < 1000*1000*1000*1000 {
		return readableSize{
			Size: bytes / (1000 * 1000 * 1000),
			Unit: "GB",
		}
	} else if bytes < 1000*1000*1000*1000*1000 {
		return readableSize{
			Size: bytes / (1000 * 1000 * 1000 * 1000),
			Unit: "TB",
		}
	} else if bytes < 1000*1000*1000*1000*1000*1000 {
		return readableSize{
			Size: bytes / (1000 * 1000 * 1000 * 1000 * 1000),
			Unit: "PB",
		}
	} else {
		return readableSize{
			Size: bytes / (1000 * 1000 * 1000 * 1000 * 1000 * 1000),
			Unit: "EB",
		}
	}
}

func smoothMeasure(old, new float64) float64 {
	const smoothing = 0.5
	return (new * smoothing) + (old * (1.0 - smoothing))
}
