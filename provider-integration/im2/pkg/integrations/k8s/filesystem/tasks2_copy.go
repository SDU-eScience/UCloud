package filesystem

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/sys/unix"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

func task2ProcessCopy(spec Task2Spec) *util.HttpError {
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
