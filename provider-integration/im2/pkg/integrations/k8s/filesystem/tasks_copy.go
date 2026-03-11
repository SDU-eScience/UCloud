package filesystem

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/sys/unix"
	ctrl "ucloud.dk/pkg/controller"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func task2ProcessCopy(spec TaskSpec) *util.HttpError {
	if os.Getenv(taskEnvId) == "" {
		log.Fatal("This code can only run inside of a task.")
		return util.HttpErr(http.StatusInternalServerError, "internal error")
	}

	sourcePath := spec.Source
	destPath := spec.Destination

	sourceFile, ok := OpenFile(sourcePath, unix.O_RDONLY, 0)
	if !ok {
		return util.HttpErr(http.StatusBadRequest, "invalid source file supplied - it no longer exists")
	}
	defer util.SilentClose(sourceFile)

	sourceStat, err := sourceFile.Stat()
	if err != nil {
		return util.HttpErr(http.StatusBadRequest, "invalid source file supplied - it no longer exists")
	}

	destFlags := unix.O_RDONLY
	destMode := uint32(0770)
	if !sourceStat.IsDir() {
		destMode = 0660
		destFlags = unix.O_RDWR | unix.O_TRUNC | unix.O_CREAT
	}

	destFile, ok := OpenFile(destPath, destFlags, destMode)
	if !ok && sourceStat.IsDir() {
		herr := DoCreateFolder(destPath)
		if herr != nil {
			return util.HttpErr(http.StatusBadRequest, "unable to open destination file")
		}

		destFile, ok = OpenFile(destPath, destFlags, destMode)
		if !ok {
			return util.HttpErr(http.StatusBadRequest, "unable to open destination file")
		}

		defer util.SilentClose(destFile)
	} else if !ok {
		return util.HttpErr(http.StatusBadRequest, "unable to open destination file")
	}

	destFileFd := int(destFile.Fd())
	destStat, err := destFile.Stat()
	if destStat != nil && err == nil {
		if sourceStat.IsDir() != destStat.IsDir() {
			return util.HttpErr(http.StatusBadRequest, "destination already exists and has the wrong type")
		}
	}

	title := util.OptValue(fmt.Sprintf(
		"Copying %s to %s",
		util.FileName(sourcePath),
		util.FileName(filepath.Dir(destPath)),
	))

	taskProcessorPostUpdate(fnd.TaskStatus{
		State:              fnd.TaskStateRunning,
		Title:              title,
		Body:               util.OptValue(""),
		Progress:           util.OptValue(""),
		ProgressPercentage: util.OptValue(0.0),
	})

	ctx, cancel := context.WithCancel(context.Background())
	var filesDiscovered atomic.Int64
	var bytesDiscovered atomic.Int64
	var filesDiscoveredDone atomic.Bool
	var workersDone atomic.Bool

	var workersWg sync.WaitGroup
	var producerWg sync.WaitGroup

	lastStatMeasurement := time.Now()
	filesPerSecond := float64(0)
	bytesPerSecond := float64(0)
	lastFilesCompleted := int64(0)
	lastBytesTransferred := int64(0)

	encounteredErrors := int64(0)

	frontendQueue := make(chan discoveredFile, 256)
	backendQueue := make(chan discoveredFile, 256)

	producerWg.Add(1)
	go func() {
		defer producerWg.Done()
		defer close(backendQueue)

	outer:
		for util.IsAlive {
			select {
			case <-ctx.Done():
				break outer

			case df, ok := <-frontendQueue:
				if !ok {
					break outer
				}

				filesDiscovered.Add(1)
				if df.LinkTo == "" {
					if !df.FileInfo.IsDir() {
						bytesDiscovered.Add(df.FileInfo.Size())
					}
				}

				select {
				case <-ctx.Done():
					break outer

				case backendQueue <- df:
				}
			}
		}

		filesDiscoveredDone.Store(true)
	}()

	producerWg.Add(1)
	go func() {
		defer producerWg.Done()
		defer close(frontendQueue)
		normalFileWalk(ctx, frontendQueue, sourceFile, sourceStat)
	}()

	var workers []*copyWorker
	for i := 0; i < 8; i++ {
		w := &copyWorker{
			DestFileFd: destFileFd,
		}
		workers = append(workers, w)

		workersWg.Add(1)
		go func(worker *copyWorker) {
			defer workersWg.Done()
			worker.Process(ctx, backendQueue)
		}(w)
	}

	go func() {
		workersWg.Wait()
		workersDone.Store(true)
	}()

	flushStats := func() {
		now := time.Now()
		dt := now.Sub(lastStatMeasurement)
		lastStatMeasurement = now

		bytesTransferred := int64(0)
		filesCompleted := int64(0)
		filesFailed := int64(0)
		for _, w := range workers {
			filesCompleted += w.FilesCompleted.Load()
			filesFailed += w.FilesFailed.Load()
			bytesTransferred += w.BytesProcessed.Load()
		}
		filesSeen := filesDiscovered.Load()
		bytesSeen := bytesDiscovered.Load()

		filesPerSecond = util.SmoothMeasure(filesPerSecond, float64(filesCompleted-lastFilesCompleted)/dt.Seconds())

		f := float64(bytesTransferred - lastBytesTransferred)
		seconds := dt.Seconds()
		bytesPerSecond = util.SmoothMeasure(bytesPerSecond, f/seconds)

		lastFilesCompleted = filesCompleted
		lastBytesTransferred = bytesTransferred

		readableSpeed := util.SizeToHumanReadableWithUnit(bytesPerSecond)

		percentage := float64(0)
		if bytesSeen > 0 {
			percentage = (float64(bytesTransferred) / float64(bytesSeen)) * 100
		}

		if !filesDiscoveredDone.Load() {
			percentage = -1
		}

		readableDataTransferred := util.SizeToHumanReadableWithUnit(float64(bytesTransferred))
		readableDiscoveredDataSize := util.SizeToHumanReadableWithUnit(float64(bytesSeen))

		body := fmt.Sprintf(
			"%.2f %v/%.2f %v | %v / %v files",
			readableDataTransferred.Size,
			readableDataTransferred.Unit,
			readableDiscoveredDataSize.Size,
			readableDiscoveredDataSize.Unit,
			filesCompleted,
			filesSeen,
		)
		if filesFailed > 0 {
			body += fmt.Sprintf(" | %v failed", filesFailed)
		}

		newStatus := fnd.TaskStatus{
			State: fnd.TaskStateRunning,
			Title: title,
			Body:  util.OptValue(body),
			Progress: util.OptValue(fmt.Sprintf(
				"%.2f %v/s | %.2f files/s",
				readableSpeed.Size,
				readableSpeed.Unit,
				filesPerSecond,
			)),
			ProgressPercentage: util.OptValue(percentage),
		}
		taskProcessorPostUpdate(newStatus)
	}

	ticker := time.NewTicker(100 * time.Millisecond)
	defer ticker.Stop()

outer:
	for util.IsAlive {
		select {
		case <-ctx.Done():
			break outer

		case <-ticker.C:
			flushStats()

			if workersDone.Load() {
				break outer
			}

			if taskProcessorIsCancelled() {
				cancel()
			}
		}
	}

	cancel()
	producerWg.Wait()
	workersWg.Wait()
	flushStats()

	for _, w := range workers {
		encounteredErrors += w.FilesFailed.Load()
	}

	util.SilentClose(sourceFile)
	util.SilentClose(destFile)

	if encounteredErrors > 0 {
		return util.HttpErr(http.StatusInternalServerError, "copy finished with %d failed entries", encounteredErrors)
	}

	if taskProcessorIsCancelled() {
		return util.HttpErr(http.StatusRequestTimeout, "task was cancelled")
	}

	return nil
}

func copyFiles(actor rpc.Actor, request orc.FilesProviderMoveOrCopyRequest) *util.HttpError {
	if !AllowUCloudPathsTogether([]string{request.OldId, request.NewId}) {
		return util.ServerHttpError("Some of these files cannot be used together. One or more are sensitive.")
	}

	_, ok1, _ := UCloudToInternal(request.OldId)
	destPath, ok2, destDrive := UCloudToInternal(request.NewId)
	if !ok1 || !ok2 {
		return &util.HttpError{
			StatusCode: http.StatusNotFound,
			Why:        "Unable to copy files. Request or destination is unknown.",
		}
	}

	if ctrl.ResourceIsLocked(destDrive.Resource, request.ResolvedNewCollection.Specification.Product) {
		return util.PaymentError()
	}

	task := TaskSpec{
		Type:           TaskSpecTypeCopy,
		Source:         request.OldId,
		Destination:    request.NewId,
		ConflictPolicy: string(request.ConflictPolicy),
		Mounts: []TaskMount{
			{UCloudPath: request.OldId},
			{UCloudPath: request.NewId},
		},
	}
	task.CreationState.Username = actor.Username
	task.CreationState.Icon = "copy"

	if request.ConflictPolicy == orc.WriteConflictPolicyRename {
		newPath, err := findAvailableNameOnRename(destPath)
		destPath = newPath

		if err != nil {
			return &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Unable to copy file (too many duplicates?)",
			}
		}

		newInternalDest, ok := InternalToUCloudWithDrive(&request.ResolvedNewCollection, destPath)
		if !ok {
			return util.UserHttpError("Unable to copy files. Unknown drive")
		}
		task.Destination = newInternalDest
	}

	return TaskSubmit(task)
}

type copyWorker struct {
	DestFileFd     int
	FilesCompleted atomic.Int64
	FilesFailed    atomic.Int64
	BytesProcessed atomic.Int64
}

func (w *copyWorker) Process(ctx context.Context, backendQueue <-chan discoveredFile) {
outer:
	for util.IsAlive {
		select {
		case <-ctx.Done():
			break outer
		case entry, ok := <-backendQueue:
			if !ok {
				break outer
			}

			bytesCopied, copied := w.copyFile(entry)
			if copied {
				w.FilesCompleted.Add(1)
				w.BytesProcessed.Add(bytesCopied)
			} else {
				w.FilesFailed.Add(1)
			}

			if entry.InternalPath != "" {
				_ = entry.FileDescriptor.Close()
			}
		}
	}
}

func (w *copyWorker) copyFile(entry discoveredFile) (int64, bool) {
	var err error = nil
	destFileFd := w.DestFileFd

	if entry.InternalPath == "" && entry.LinkTo == "" && entry.FileInfo != nil && entry.FileInfo.IsDir() {
		return 0, true
	}

	for i := 0; i < len(entry.InternalPath); i++ {
		if entry.InternalPath[i] == '/' {
			err := unix.Mkdirat(destFileFd, entry.InternalPath[:i], 0770)
			if err != nil && !errors.Is(err, unix.ENOTDIR) && !errors.Is(err, unix.EEXIST) {
				log.Info("Failed to create directory: %s (%#v)", err, entry)
				return 0, false
			}
		}
	}

	if entry.LinkTo != "" {
		return 0, unix.Symlinkat(entry.LinkTo, destFileFd, entry.InternalPath) == nil
	} else if !entry.FileInfo.IsDir() {
		fd := int(0)
		if entry.InternalPath == "" {
			fd = destFileFd
		} else {
			fd, err = unix.Openat(destFileFd, entry.InternalPath, unix.O_RDWR|unix.O_TRUNC|unix.O_CREAT, 0660)
			if err != nil {
				log.Info("Failed to open: %s (%#v)", err, entry)
				return 0, false
			}
		}

		_ = unix.Fchmod(fd, uint32(entry.FileInfo.Mode().Perm()))
		_ = unix.Fchown(fd, FileUid(entry.FileInfo), FileGid(entry.FileInfo))
		file := os.NewFile(uintptr(fd), entry.InternalPath)
		bytesCopied, err := io.Copy(file, entry.FileDescriptor)
		_ = file.Close()
		if err != nil {
			log.Info("Failed to copy: %s (%#v)", err, entry)
			return 0, false
		}

		return bytesCopied, true
	} else {
		_ = unix.Mkdirat(destFileFd, entry.InternalPath, 0770)
		_ = unix.Fchownat(destFileFd, entry.InternalPath, FileUid(entry.FileInfo), FileGid(entry.FileInfo), 0)
		err := unix.Fchmodat(destFileFd, entry.InternalPath, uint32(entry.FileInfo.Mode().Perm()), 0)
		if err != nil {
			log.Info("Failed to copy: %s (%#v)", err, entry)
		}
		return 0, err == nil
	}
}

type discoveredFile struct {
	InternalPath   string
	FileDescriptor *os.File
	FileInfo       os.FileInfo
	LinkTo         string
}

func normalFileWalk(ctx context.Context, output chan discoveredFile, rootFile *os.File, rootFileInfo os.FileInfo) {
	f := discoveredFile{
		InternalPath:   "",
		FileDescriptor: rootFile,
		FileInfo:       rootFileInfo,
	}

	select {
	case <-ctx.Done():
	case output <- f:
	}

	if !rootFileInfo.IsDir() {
		return
	}

	dir, err := rootFile.Readdirnames(-1)
	if err != nil {
		return
	}

	stack := dir

outer:
	for util.IsAlive && len(stack) > 0 {
		select {
		case <-ctx.Done():
			break outer
		default:
			last := len(stack) - 1
			entry := stack[last]
			stack = stack[:last]

			fd, err := unix.Openat(int(rootFile.Fd()), entry, unix.O_RDONLY|unix.O_NOFOLLOW, 0)
			if err != nil {
				if errors.Is(err, unix.ELOOP) {
					var stat unix.Stat_t
					err = unix.Fstatat(int(rootFile.Fd()), entry, &stat, unix.AT_SYMLINK_NOFOLLOW)
					if err != nil {
						continue outer
					}

					bufSize := int(stat.Size)

					// NOTE(Dan): This is one larger in order to be able to detect link target being truncated. We could
					// in theory retry if it turns out that the link is changed between us reading the stat and the link
					// being fetched, but this is not a case we truly care about. This function already gives undefined
					// behavior in case you are actively changing the input.
					buf := make([]byte, bufSize+1)

					n, err := unix.Readlinkat(int(rootFile.Fd()), entry, buf)
					if err == nil && n >= 0 && n <= bufSize {
						df := discoveredFile{
							InternalPath: entry,
							LinkTo:       string(buf[:n]),
						}

						select {
						case <-ctx.Done():
							break outer
						case output <- df:
						}
					}
				}

				continue outer
			}

			file := os.NewFile(uintptr(fd), entry)
			stat, err := file.Stat()
			if err != nil {
				util.SilentClose(file)
			} else {
				df := discoveredFile{
					InternalPath:   entry,
					FileDescriptor: file,
					FileInfo:       stat,
				}

				if stat.IsDir() {
					// NOTE(Dan): Ignore errors and just continue. The consumer is left to handle
					// this edge-case.
					children, err := file.Readdirnames(-1)
					if err == nil {
						for _, child := range children {
							stack = append(stack, entry+"/"+child)
						}
					}
				}

				select {
				case <-ctx.Done():
					break outer
				case output <- df:
				}
			}
		}
	}
}
