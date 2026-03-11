package filesystem

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"sync/atomic"
	"time"

	"golang.org/x/sys/unix"
	"ucloud.dk/pkg/controller/upload"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func task2ProcessTransfer(spec TaskSpec) *util.HttpError {
	if os.Getenv(taskEnvId) == "" {
		log.Fatal("This code can only run inside of a task.")
		return util.HttpErr(http.StatusInternalServerError, "internal error")
	}

	uploadSession := upload.ClientSession{
		Endpoint:       spec.TransferEndpoint,
		ConflictPolicy: orc.WriteConflictPolicyMergeRename,
	}

	log.Info("%#v", uploadSession)

	internalSource := spec.Source

	const numberOfAttempts = 10
	var uploadErr *util.HttpError = nil
	for i := 0; i < numberOfAttempts; i++ {
		// NOTE(Dan): The rootFile is automatically closed by the uploader
		rootFile, ok := OpenFile(internalSource, unix.O_RDONLY, 0)
		if !ok {
			return util.HttpErr(http.StatusBadRequest, "unable to open source file")
		}

		uploaderRoot := &uploaderClientFile{
			Path: "",
			File: rootFile,
		}

		finfo, err := rootFile.Stat()
		if err != nil {
			return util.HttpErr(http.StatusBadRequest, "unable to open source file")
		}
		ftype := upload.FileTypeFile
		if finfo.IsDir() {
			ftype = upload.FileTypeDirectory
		}
		rootMetadata := upload.FileMetadata{
			Size:         finfo.Size(),
			ModifiedAt:   fnd.Timestamp(finfo.ModTime()),
			InternalPath: "",
			Type:         ftype,
		}

		status := atomic.Pointer[fnd.TaskStatus]{}

		transferCtx, cancelTransfer := context.WithCancel(context.Background())
		statusDone := make(chan struct{})
		go func() {
			defer close(statusDone)
			ticker := time.NewTicker(250 * time.Millisecond)
			defer ticker.Stop()

			var lastStatus *fnd.TaskStatus

			for {
				select {
				case <-transferCtx.Done():
					return

				case <-ticker.C:
					if taskProcessorIsCancelled() {
						cancelTransfer()
						continue
					}

					taskStatus := status.Load()
					if taskStatus != nil && lastStatus != taskStatus {
						lastStatus = taskStatus
						copied := *taskStatus
						if !copied.Title.Present {
							copied.Title.Set(fmt.Sprintf("File transfer of %s", util.FileName(spec.Source)))
						}

						taskProcessorPostUpdate(copied)
					}
				}
			}
		}()

		report := upload.ProcessClient(transferCtx, uploadSession, uploaderRoot, rootMetadata, &status)
		cancelTransfer()
		<-statusDone

		if report.WasCancelledByUser {
			uploadErr = nil
			break
		}

		if !report.NormalExit {
			i--
			time.Sleep(5 * time.Second)
			continue
		}

		if report.BytesTransferred == 0 && report.NewFilesUploaded == 0 {
			uploadErr = nil
			break
		}

		if i == numberOfAttempts-1 {
			uploadErr = util.ServerHttpError("unable to upload all files")
		}
	}

	if uploadErr == nil {
		if !upload.CloseSessionFromClient(uploadSession) {
			return util.HttpErr(http.StatusInternalServerError, "failed to close upload session")
		}
	}

	return uploadErr
}
