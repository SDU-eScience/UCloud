package filesystem

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sync/atomic"
	"time"

	"golang.org/x/sys/unix"
	ctrl "ucloud.dk/pkg/controller"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

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

	if ctrl.IsResourceLocked(destDrive.Resource, request.ResolvedNewCollection.Specification.Product) {
		return util.PaymentError()
	}

	task := TaskInfoSpecification{
		Type:              FileTaskTypeCopy,
		CreatedAt:         fnd.Timestamp(time.Now()),
		UCloudUsername:    actor.Username,
		UCloudSource:      util.OptValue(request.OldId),
		UCloudDestination: util.OptValue(request.NewId),
		ConflictPolicy:    request.ConflictPolicy,
		HasUCloudTask:     true,
		Icon:              "copy",
	}

	if task.ConflictPolicy == orc.WriteConflictPolicyRename {
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
		task.UCloudDestination.Set(newInternalDest)
	}

	return RegisterTask(task)
}

func processCopyTask(task *TaskInfo) TaskProcessingResult {
	sourcePath, ok1, _ := UCloudToInternal(task.UCloudSource.Value)
	destPath, ok2, _ := UCloudToInternal(task.UCloudDestination.Value)
	if !ok1 || !ok2 {
		return TaskProcessingResult{
			Error: fmt.Errorf("Invalid source or destination supplied"),
		}
	}

	sourceFile, ok := OpenFile(sourcePath, unix.O_RDONLY, 0)
	if !ok {
		return TaskProcessingResult{
			Error: fmt.Errorf("Invalid source file supplied. It no longer exists."),
		}
	}
	defer util.SilentClose(sourceFile)

	sourceStat, err := sourceFile.Stat()
	if err != nil {
		return TaskProcessingResult{
			Error: fmt.Errorf("Invalid source file supplied. It no longer exists."),
		}
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
			return TaskProcessingResult{
				Error: fmt.Errorf("Unable to open destination file"),
			}
		}

		destFile, ok = OpenFile(destPath, destFlags, destMode)
		if !ok {
			return TaskProcessingResult{
				Error: fmt.Errorf("Unable to open destination file"),
			}
		}

		defer util.SilentClose(destFile)
	} else if err != nil {
		return TaskProcessingResult{
			Error: fmt.Errorf("Unable to open destination file"),
		}
	}

	destFileFd := int(destFile.Fd())
	destStat, err := destFile.Stat()
	if destStat != nil && err == nil {
		if sourceStat.IsDir() != destStat.IsDir() {
			return TaskProcessingResult{
				Error: fmt.Errorf("destination already exists and has the wrong type"),
			}
		}
	}

	title := util.OptValue(fmt.Sprintf(
		"Copying %s to %s",
		util.FileName(sourcePath),
		util.FileName(filepath.Dir(destPath)),
	))

	task.Status.Store(&fnd.TaskStatus{
		State:              fnd.TaskStateRunning,
		Title:              title,
		Body:               util.OptValue(""),
		Progress:           util.OptValue(""),
		ProgressPercentage: util.OptValue(0.0),
	})

	ctx, cancel := context.WithCancel(context.Background())
	filesDiscovered := int64(0)
	bytesDiscovered := int64(0)
	filesDiscoveredDone := false

	lastStatMeasurement := time.Now()
	filesPerSecond := float64(0)
	bytesPerSecond := float64(0)
	lastFilesCompleted := int64(0)
	lastBytesTransferred := int64(0)

	// wasCancelledByUser := false

	frontendQueue := make(chan discoveredFile, 256)
	backendQueue := make(chan discoveredFile, 256)

	go func() {
	outer:
		for util.IsAlive {
			select {
			case <-ctx.Done():
				break outer

			case df := <-frontendQueue:
				filesDiscovered++
				if df.LinkTo == "" {
					if !df.FileInfo.IsDir() {
						bytesDiscovered += df.FileInfo.Size()
					}
				}

				select {
				case <-ctx.Done():
					break outer

				case backendQueue <- df:
				}
			}
		}
	}()

	go func() {
		normalFileWalk(ctx, frontendQueue, sourceFile, sourceStat)
		filesDiscoveredDone = true
	}()

	var workers []*copyWorker
	for i := 0; i < 8; i++ {
		w := &copyWorker{
			DestFileFd: destFileFd,
		}
		workers = append(workers, w)

		go w.Process(ctx, backendQueue)
	}

	flushStats := func() {
		now := time.Now()
		dt := now.Sub(lastStatMeasurement)
		lastStatMeasurement = now

		bytesTransferred := int64(0)
		filesCompleted := int64(0)
		for _, w := range workers {
			filesCompleted += w.FilesCompleted.Load()
			bytesTransferred += w.BytesProcessed.Load()
		}

		filesPerSecond = util.SmoothMeasure(filesPerSecond, float64(filesCompleted-lastFilesCompleted)/dt.Seconds())

		f := float64(bytesTransferred - lastBytesTransferred)
		seconds := dt.Seconds()
		bytesPerSecond = util.SmoothMeasure(bytesPerSecond, f/seconds)

		lastFilesCompleted = filesCompleted
		lastBytesTransferred = bytesTransferred

		readableSpeed := util.SizeToHumanReadableWithUnit(bytesPerSecond)

		percentage := (float64(bytesTransferred) / float64(bytesDiscovered)) * 100
		if !filesDiscoveredDone {
			percentage = -1
		}

		readableDataTransferred := util.SizeToHumanReadableWithUnit(float64(bytesTransferred))
		readableDiscoveredDataSize := util.SizeToHumanReadableWithUnit(float64(bytesDiscovered))

		newStatus := &fnd.TaskStatus{
			State: fnd.TaskStateRunning,
			Title: title,
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
		task.Status.Store(newStatus)
	}

	ticker := time.NewTicker(100 * time.Millisecond)

outer:
	for util.IsAlive {
		select {
		case <-ctx.Done():
			break outer

		case <-ticker.C:
			flushStats()

			if filesDiscoveredDone && lastFilesCompleted == filesDiscovered {
				cancel()
			}
		}
	}

	cancel()

	util.SilentClose(sourceFile)
	util.SilentClose(destFile)

	return TaskProcessingResult{}
}

type copyWorker struct {
	DestFileFd     int
	FilesCompleted atomic.Int64
	BytesProcessed atomic.Int64
}

func (w *copyWorker) Process(ctx context.Context, backendQueue chan discoveredFile) {
outer:
	for util.IsAlive {
		select {
		case <-ctx.Done():
			break outer
		case entry := <-backendQueue:
			w.copyFile(entry)
			w.FilesCompleted.Add(1)
			if entry.LinkTo == "" && !entry.FileInfo.IsDir() {
				w.BytesProcessed.Add(entry.FileInfo.Size())
			}

			if entry.InternalPath != "" {
				_ = entry.FileDescriptor.Close()
			}
		}
	}
}

func (w *copyWorker) copyFile(entry discoveredFile) bool {
	var err error = nil
	destFileFd := w.DestFileFd

	for i := 0; i < len(entry.InternalPath); i++ {
		if entry.InternalPath[i] == '/' {
			err := unix.Mkdirat(destFileFd, entry.InternalPath[:i], 0770)
			if !errors.Is(err, unix.ENOTDIR) && !errors.Is(err, unix.EEXIST) {
				return false
			}
		}
	}

	if entry.LinkTo != "" {
		return unix.Symlinkat(entry.LinkTo, destFileFd, entry.InternalPath) == nil
	} else if !entry.FileInfo.IsDir() {
		fd := int(0)
		if entry.InternalPath == "" {
			fd = destFileFd
		} else {
			fd, err = unix.Openat(destFileFd, entry.InternalPath, unix.O_RDWR|unix.O_TRUNC|unix.O_CREAT, 0660)
			if err != nil {
				return false
			}
		}

		_ = unix.Fchmod(fd, uint32(entry.FileInfo.Mode().Perm()))
		_ = unix.Fchown(fd, FileUid(entry.FileInfo), FileGid(entry.FileInfo))
		file := os.NewFile(uintptr(fd), entry.InternalPath)
		_, err = io.Copy(file, entry.FileDescriptor)
		_ = file.Close()
		if err != nil {
			return false
		}
	} else {
		_ = unix.Mkdirat(destFileFd, entry.InternalPath, 0770)
		_ = unix.Fchownat(destFileFd, entry.InternalPath, FileUid(entry.FileInfo), FileGid(entry.FileInfo), 0)
		return unix.Fchmodat(destFileFd, entry.InternalPath, uint32(entry.FileInfo.Mode().Perm()), 0) == nil
	}

	return true
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
					if n == bufSize && err == nil {
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
