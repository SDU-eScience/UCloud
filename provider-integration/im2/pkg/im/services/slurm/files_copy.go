package slurm

import (
	"context"
	"errors"
	"fmt"
	"golang.org/x/sys/unix"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sync/atomic"
	"time"
	fnd "ucloud.dk/shared/pkg/foundation"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func copyFiles(request ctrl.CopyFileRequest) error {
	_, ok1 := UCloudToInternal(request.OldPath)
	destPath, ok2 := UCloudToInternal(request.NewPath)
	if !ok1 || !ok2 {
		return &util.HttpError{
			StatusCode: http.StatusNotFound,
			Why:        "Unable to copy files. Request or destination is unknown.",
		}
	}

	task := TaskInfoSpecification{
		Type:              FileTaskTypeCopy,
		CreatedAt:         fnd.Timestamp(time.Now()),
		UCloudSource:      util.OptValue(request.OldPath),
		UCloudDestination: util.OptValue(request.NewPath),
		ConflictPolicy:    request.Policy,
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

		newInternalDest := InternalToUCloudWithDrive(request.NewDrive, destPath)
		task.UCloudDestination.Set(newInternalDest)
	}

	return RegisterTask(task)
}

func processCopyTask(task *TaskInfo) TaskProcessingResult {
	sourcePath, ok1 := UCloudToInternal(task.UCloudSource.Value)
	destPath, ok2 := UCloudToInternal(task.UCloudDestination.Value)
	if !ok1 || !ok2 {
		return TaskProcessingResult{
			Error: fmt.Errorf("Invalid source or destination supplied"),
		}
	}

	sourceFile, err := os.Open(sourcePath)
	if err != nil {
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

	destFlags := os.O_RDONLY
	destMode := os.FileMode(0770)
	if !sourceStat.IsDir() {
		destMode = os.FileMode(0660)
		destFlags = os.O_RDWR | os.O_TRUNC | os.O_CREATE
	}

	destFile, err := os.OpenFile(destPath, destFlags, destMode)
	if err != nil && os.IsNotExist(err) && sourceStat.IsDir() {
		err = os.Mkdir(destPath, 0770)
		if err != nil {
			return TaskProcessingResult{
				Error: fmt.Errorf("Unable to open destination file"),
			}
		}

		destFile, err = os.OpenFile(destPath, destFlags, destMode)
		if err != nil {
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

	task.Status.Store(&orc.TaskStatus{
		State:              orc.TaskStateRunning,
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

		newStatus := &orc.TaskStatus{
			State: orc.TaskStateRunning,
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
				log.Info("Could not write file? %v", entry.InternalPath)
				return false
			}
		}

		_ = unix.Fchmod(fd, uint32(entry.FileInfo.Mode().Perm()))
		file := os.NewFile(uintptr(fd), entry.InternalPath)
		_, err = io.Copy(file, entry.FileDescriptor)
		_ = file.Close()
		if err != nil {
			return false
		}
	} else {
		_ = unix.Mkdirat(destFileFd, entry.InternalPath, 0770)
		return unix.Fchmodat(destFileFd, entry.InternalPath, uint32(entry.FileInfo.Mode().Perm()), 0) == nil
	}

	return true
}
