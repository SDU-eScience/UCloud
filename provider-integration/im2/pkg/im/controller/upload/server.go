package upload

import (
	"bytes"
	"context"
	"fmt"
	ws "github.com/gorilla/websocket"
	"golang.org/x/sync/semaphore"
	"strings"
	"sync"
	"sync/atomic"
	"time"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type FileType int

const (
	FileTypeFile FileType = iota
	FileTypeDirectory
)

type FileMetadata struct {
	Size         int64
	ModifiedAt   fnd.Timestamp
	InternalPath string
	Type         FileType
}

type ServerSession struct {
	Id             string
	ConflictPolicy orc.WriteConflictPolicy
	Path           string
	UserData       string
}

type ServerMetrics struct {
	lock             sync.Mutex
	BytesTransferred int64
	FilesCompleted   int64
	FilesSkipped     int64
	SkipReasons      map[string]SkipReason
}

func (m *ServerMetrics) CompleteFile(size int64) {
	m.lock.Lock()
	defer m.lock.Unlock()

	m.BytesTransferred += size
	m.FilesCompleted++
}

func (m *ServerMetrics) SkipFile(reason string, path string) {
	m.lock.Lock()
	defer m.lock.Unlock()

	m.FilesSkipped++
	truncatedReason := reason[:min(len(reason), 16)]
	skips, _ := m.SkipReasons[truncatedReason]
	skips.AddSkip(reason, path)
	m.SkipReasons[reason] = skips
}

func (m *ServerMetrics) Absorb(other *ServerMetrics) {
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

type ServerFileSystem interface {
	OpenFileIfNeeded(session ServerSession, fileMeta FileMetadata) ServerFile
	OnSessionClose(session ServerSession, success bool)
}

type ServerFile interface {
	Write(ctx context.Context, data []byte) error
	Close()
}

type serverFileUpload struct {
	FileSystem         ServerFileSystem
	Session            ServerSession
	Entry              messageListing
	Ctx                context.Context
	OpenFilesSemaphore *semaphore.Weighted
	ChunkSemaphore     *semaphore.Weighted
	ChunksOrSkip       chan []byte
	Done               atomic.Bool

	// Valid values for Status
	// - 0: The file handler has not yet made a decision if this file should be processed
	// - 1: This file should be processed, but this has not yet been sent to the client
	// - 2: This file should not be processed, but this has not yet been sent to the client
	// - 3: The client has been notified about the decision
	Status atomic.Int32

	Metrics *ServerMetrics
}

func (f *serverFileUpload) Process() {
	err := f.OpenFilesSemaphore.Acquire(f.Ctx, 1)
	if err != nil {
		f.Done.Store(true)
		return
	}

	file := f.FileSystem.OpenFileIfNeeded(f.Session, FileMetadata{
		Size:         f.Entry.Size,
		ModifiedAt:   fnd.TimeFromUnixMilli(uint64(f.Entry.ModifiedAt)),
		InternalPath: f.Entry.Path,
	})

	if file != nil {
		f.Status.Swap(1)

		written := int64(0)

		didWriteErrorMetrics := false
		if f.Entry.Size > 0 {
		outer:
			for {
				select {
				case <-f.Ctx.Done():
					break outer

				case chunk := <-f.ChunksOrSkip:
					if chunk == nil {
						break outer
					}

					err = file.Write(f.Ctx, chunk)
					if err != nil && !didWriteErrorMetrics {
						// NOTE(Dan): Writes in this case must continue to work even if the write has failed. We only
						// use the error for metrics.
						f.Metrics.SkipFile(err.Error(), f.Entry.Path)
						didWriteErrorMetrics = true
					}
					f.ChunkSemaphore.Release(chunkWeight(chunk))

					written += int64(len(chunk))
					if written >= f.Entry.Size {
						break outer
					}
				}
			}
		}

		f.Metrics.CompleteFile(f.Entry.Size)
	} else {
		f.Status.Swap(2)
		f.Metrics.SkipFile("not needed", f.Entry.Path)
	}

	if file != nil {
		file.Close()
	}

	f.Done.Store(true)
	f.OpenFilesSemaphore.Release(1)
}

func chunkWeight(chunk []byte) int64 {
	return int64(len(chunk))
}

func ProcessServer(socket *ws.Conn, fs ServerFileSystem, session ServerSession) bool {
	uploads := map[int32]*serverFileUpload{}

	metrics := ServerMetrics{
		SkipReasons: make(map[string]SkipReason),
	}

	// Acquired and released by the serverFileUpload goroutines. This is acquired by the goroutine since we want to keep
	// processing network messages even if there are too many for us to handle right now. The client won't send us any
	// data packets about the file until we acknowledge the file, which requires the semaphore.
	openFilesSemaphore := semaphore.NewWeighted(64)

	// Acquired by the main goroutine (this one) and released by the serverFileUpload goroutine. This is acquired in
	// the main goroutine to ensure that we do not accumulate a backlog of too many data packets. This way, we will
	// simply stop acknowledging the network traffic which should ensure that we don't suddenly run out of memory.
	chunkSemaphore := semaphore.NewWeighted(1024 * 1024 * 64)

	sessionContext, cancel := context.WithCancel(context.Background())
	maintenanceTicker := time.NewTicker(maintenanceTickRateMs * time.Millisecond)
	terminationTimer := terminationTimeoutTicks

	// NOTE(Dan): This channel is bounded to make sure that we don't create a massive backlog of unprocessed data
	incomingWebsocket := make(chan []byte, 1)
	go func() {
		for {
			_, data, readErr := socket.ReadMessage()
			if readErr != nil {
				break
			}

			incomingWebsocket <- data
		}

		cancel()
	}()

	completedFiles := int32(0)
	knownFiles := int32(0)

	// NOTE(Dan): Since there will be no chunks or file listings, we can just use a single frame for all the
	// messages we intend to send.
	outputBuffer := util.NewBuffer(&bytes.Buffer{})

	wasClosed := false

outer:
	for {
		select {
		case <-sessionContext.Done():
			break outer

		case <-maintenanceTicker.C:
			outputBuffer.Reset()

			completedFilesAtStart := completedFiles

			for id, upload := range uploads {
				if upload.Done.Load() {
					completedFiles++

					if completedFiles == knownFiles {
						terminationTimer = 20 * 30
					}
					delete(uploads, id)
				}

				currentStatus := upload.Status.Swap(3)
				switch currentStatus {
				case 1:
					// Process it
					(&messageOk{FileId: id}).Marshal(outputBuffer, true)

				case 2:
					// Do not process it
					(&messageSkip{FileId: id}).Marshal(outputBuffer, true)
				}
			}

			if completedFiles != completedFilesAtStart {
				(&messageCompleted{NumberOfProcessedFiles: completedFiles}).Marshal(outputBuffer, true)
			}

			outputBytes := outputBuffer.ReadRemainingBytes()
			if len(outputBytes) > 0 {
				err := socket.WriteMessage(ws.BinaryMessage, outputBytes)
				if err != nil {
					break outer
				}
			}

			if terminationTimer > 0 {
				terminationTimer--
				if terminationTimer == 0 {
					break outer
				}
			}

		case data, ok := <-incomingWebsocket:
			if !ok {
				break outer
			}

			if len(data) == 4 && string(data) == "ping" {
				err := socket.WriteMessage(ws.TextMessage, []byte("pong"))
				if err != nil {
					break outer
				}

				continue
			}

			buf := util.NewBuffer(bytes.NewBuffer(data))
			t := messageType(buf.ReadU8())

			// NOTE(Dan): We did a stupid thing here in the past, but let's roll with it. All messages are allowed to be
			// repeated in a single frame, with the opcode at the beginning of each message. The only exception to this,
			// is the file listing, which will not repeat the opcode. For file listings, it is assumed that the 100%
			// of the remaining frame will only contain listing entries.
			mustReadOpcode := false

			for !buf.IsEmpty() {
				if mustReadOpcode {
					t = messageType(buf.ReadU8())
				}

				rawMessage := parseMessage(t, buf)
				if rawMessage == nil {
					break
				}

				switch msg := rawMessage.(type) {
				case *messageListing:
					_, existsAlready := uploads[msg.FileId]
					if existsAlready {
						log.Warn("Received a chunk we already knew about with ID: %v at path: %v", msg.FileId, msg.Path)
						break outer
					}

					fileUpload := &serverFileUpload{
						FileSystem:         fs,
						Session:            session,
						Entry:              *msg,
						Ctx:                sessionContext,
						OpenFilesSemaphore: openFilesSemaphore,
						ChunkSemaphore:     chunkSemaphore,
						ChunksOrSkip:       make(chan []byte),
						Done:               atomic.Bool{},
						Metrics:            &metrics,
					}

					uploads[msg.FileId] = fileUpload
					knownFiles++
					terminationTimer = 0

					go fileUpload.Process()

				case *messageSkip:
					upload, ok := uploads[msg.FileId]
					if !ok {
						log.Warn("Client wants to skip an unknown chunk with ID %v", msg.FileId)
						break outer
					}

					upload.ChunksOrSkip <- nil

				case *messageChunk:
					// NOTE(Dan): Ignore chunks which are empty, the serverFileUpload should already take care of these
					if len(msg.Data) > 0 {
						err := chunkSemaphore.Acquire(sessionContext, chunkWeight(msg.Data))
						if err != nil {
							break outer
						}

						upload, ok := uploads[msg.FileId]
						if !ok {
							log.Warn("Received an unknown chunk with ID %v (len = %v)", msg.FileId, len(msg.Data))
							break outer
						}

						upload.ChunksOrSkip <- msg.Data
					}

				case *messageClose:
					wasClosed = true
					break outer

				default:
					break outer
				}

				mustReadOpcode = t != messageTypeListing
			}
		}
	}

	// Either the client or the server has decided to close the connection. Terminate everything.
	cancel()
	util.SilentClose(socket)

	// Count all completed files before exit
	for id, upload := range uploads {
		if upload.Done.Load() {
			completedFiles++
			delete(uploads, id)
		}
	}

	fs.OnSessionClose(session, wasClosed)

	if metrics.FilesCompleted != 0 || metrics.FilesSkipped != 0 || metrics.BytesTransferred != 0 {
		b := strings.Builder{}
		b.WriteString(fmt.Sprintf(
			"Upload session complete (server): %v file(s) transferred | %v bytes transferred | %v file(s) skipped\n",
			metrics.FilesCompleted,
			metrics.BytesTransferred,
			metrics.FilesSkipped,
		))
		for trunc, f := range metrics.SkipReasons {
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
	return wasClosed
}

const maintenanceTickRateMs = 50
const terminationTimeoutMs = 30000
const terminationTimeoutTicks = terminationTimeoutMs / maintenanceTickRateMs
