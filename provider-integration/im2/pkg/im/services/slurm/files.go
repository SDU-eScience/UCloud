package slurm

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"slices"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/gorilla/websocket"
	lru "github.com/hashicorp/golang-lru/v2/expirable"
	fnd "ucloud.dk/pkg/foundation"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var browseCache *lru.LRU[string, []cachedDirEntry]
var tasksInitializedMutex sync.Mutex
var tasksInitialized []string
var uploadDescriptors UploadDescriptors

type UploadSessionData struct {
	ConflictPolicy orc.WriteConflictPolicy
	Path           string
	Type           ctrl.UploadType
}

type FileListingEntry struct {
	Id         uint
	Path       string
	Size       uint64
	ModifiedAt time.Time
	Offset     uint64
}

type FolderUploadMessageType uint8

const (
	FolderUploadMessageTypeOk             FolderUploadMessageType = 0
	FolderUploadMessageTypeChecksum       FolderUploadMessageType = 1
	FolderUploadMessageTypeChunk          FolderUploadMessageType = 2
	FolderUploadMessageTypeSkip           FolderUploadMessageType = 3
	FolderUploadMessageTypeListing        FolderUploadMessageType = 4
	FolderUploadMessageTypeFilesCompleted FolderUploadMessageType = 5
)

func (t FolderUploadMessageType) Int() int8 {
	for i := 0; i <= 5; i++ {
		if FolderUploadMessageType(i) == t {
			return int8(i)
		}
	}

	return 0
}

func InitializeFiles() ctrl.FileService {
	uploadDescriptors.startMonitoringLoop()
	browseCache = lru.NewLRU[string, []cachedDirEntry](256, nil, 5*time.Minute)
	return ctrl.FileService{
		BrowseFiles:           browse,
		RetrieveFile:          retrieve,
		CreateFolder:          createFolder,
		Move:                  moveFiles,
		Copy:                  copyFiles,
		MoveToTrash:           moveToTrash,
		EmptyTrash:            emptyTrash,
		CreateDownloadSession: createDownload,
		CreateUploadSession:   createUpload,
		HandleUploadWs:        uploadWs,
		Download:              download,
	}
}

func initializeFileTasks(driveId string) {
	tasksInitializedMutex.Lock()
	defer tasksInitializedMutex.Unlock()
	var found = false

	for _, initializedTask := range tasksInitialized {
		if initializedTask == driveId {
			found = true
			break
		}
	}

	if !found {
		tasksInitialized = append(tasksInitialized, driveId)
		currentTasks := RetrieveCurrentTasks(driveId)

		for _, task := range currentTasks {
			task.process(false)
		}
	}
}

type cachedDirEntry struct {
	absPath string
	hasInfo bool
	skip    bool
	info    os.FileInfo
}

func compareFileByPath(a, b cachedDirEntry) int {
	return strings.Compare(a.absPath, b.absPath)
}

func compareFileBySize(a, b cachedDirEntry) int {
	if a.hasInfo && !b.hasInfo {
		return -1
	} else if !a.hasInfo && b.hasInfo {
		return 1
	} else if !a.hasInfo && !b.hasInfo {
		return strings.Compare(a.absPath, b.absPath)
	}

	aSize := a.info.Size()
	bSize := b.info.Size()
	if aSize < bSize {
		return -1
	} else if aSize > bSize {
		return 1
	} else {
		return strings.Compare(a.absPath, b.absPath)
	}
}

func compareFileByModifiedAt(a, b cachedDirEntry) int {
	if a.hasInfo && !b.hasInfo {
		return -1
	} else if !a.hasInfo && b.hasInfo {
		return 1
	} else if !a.hasInfo && !b.hasInfo {
		return strings.Compare(a.absPath, b.absPath)
	}

	aModTime := a.info.ModTime()
	bModTime := b.info.ModTime()
	if aModTime.Before(bModTime) {
		return -1
	} else if aModTime.After(bModTime) {
		return 1
	} else {
		return strings.Compare(a.absPath, b.absPath)
	}
}

func browse(request ctrl.BrowseFilesRequest) (fnd.PageV2[orc.ProviderFile], error) {
	internalPath := UCloudToInternalWithDrive(request.Drive, request.Path)
	sortBy := request.SortBy

	initializeFileTasks(request.Drive.Id)

	fileList, ok := browseCache.Get(internalPath)
	if !ok || request.Next == "" {
		// NOTE(Dan): Never perform caching on the initial page. This way, if a user refreshes a page they will always
		// get up-to-date results. The caching is mostly meant to deal with extremely large folders (e.g. more than
		// 1 million files). When dealing with large folders, the readdir syscall ends up taking a very significant
		// amount of time, often significantly more than the combined time of calling stat on the files in a single
		// page.
		file, err := os.Open(internalPath)
		defer util.SilentClose(file)

		if err != nil {
			// TODO(Dan): Group membership is cached in Linux. We may need to trigger a restart of the IM if the user
			//   was just added to the project. See the current Kotlin implementation for more details.
			log.Info("Could not open directory at %v %v", internalPath, err)
			return fnd.EmptyPage[orc.ProviderFile](), &util.HttpError{
				StatusCode: http.StatusNotFound,
				Why:        "Could not find directory",
			}
		}

		fileNames, err := file.Readdirnames(-1)
		if err != nil {
			return fnd.EmptyPage[orc.ProviderFile](), &util.HttpError{
				StatusCode: http.StatusNotFound,
				Why:        "Could not find and read directory. Is this a directory?",
			}
		}

		if len(fileNames) > 10000 {
			sortBy = "PATH"
		}

		entries := make([]cachedDirEntry, len(fileNames))
		for i, fileName := range fileNames {
			entries[i].absPath = fmt.Sprintf("%v/%v", internalPath, fileName)
		}

		if sortBy != "PATH" {
			// We must stat all files immediately to get info about them
			for i := 0; i < len(entries); i++ {
				entry := &entries[i]
				stat, err := os.Stat(entry.absPath)
				if err == nil {
					entry.hasInfo = true
					entry.info = stat
				} else {
					entry.skip = true
				}
			}
		}

		cmpFunction := compareFileByPath
		switch sortBy {
		case "PATH":
			cmpFunction = compareFileByPath
		case "SIZE":
			cmpFunction = compareFileBySize
		case "MODIFIED_AT":
			cmpFunction = compareFileByModifiedAt
		}

		slices.SortFunc(entries, cmpFunction)
		if request.SortDirection == orc.SortDirectionDescending {
			slices.Reverse(entries)
		}

		fileList = entries
		browseCache.Add(internalPath, entries)
	}

	offset := 0
	if request.Next != "" {
		converted, err := strconv.ParseInt(request.Next, 10, 64)
		if err != nil {
			offset = int(converted)
		}
	}

	if offset >= len(fileList) || offset < 0 {
		return fnd.EmptyPage[orc.ProviderFile](), nil
	}

	items := make([]orc.ProviderFile, min(request.ItemsPerPage, len(fileList)-offset))

	itemIdx := 0
	i := offset
	for i < len(fileList) && itemIdx < request.ItemsPerPage {
		item := &items[itemIdx]
		entry := &fileList[i]
		i++

		if !entry.hasInfo && !entry.skip {
			stat, err := os.Stat(entry.absPath)
			if err == nil {
				entry.hasInfo = true
				entry.info = stat
			}
		}

		if !entry.hasInfo {
			continue
		} else {
			itemIdx += 1
		}

		readMetadata(entry.absPath, entry.info, item, request.Drive)
	}

	nextToken := util.OptNone[string]()
	if i < len(fileList) {
		nextToken.Set(fmt.Sprintf("%v", i))
	}

	return fnd.PageV2[orc.ProviderFile]{
		Items:        items[:itemIdx],
		ItemsPerPage: 250,
		Next:         nextToken,
	}, nil
}

func retrieve(request ctrl.RetrieveFileRequest) (orc.ProviderFile, error) {
	bah, _ := json.Marshal(request)
	log.Info("retrieve drive='%v' path='%v' %v", request.Drive.Id, request.Path, string(bah))
	var result orc.ProviderFile
	internalPath := UCloudToInternalWithDrive(request.Drive, request.Path)
	stat, err := os.Stat(internalPath)
	if err != nil {
		return result, &util.HttpError{
			StatusCode: http.StatusNotFound,
			Why:        "Could not find file!",
		}
	}

	readMetadata(internalPath, stat, &result, request.Drive)
	return result, nil
}

func createFolder(request ctrl.CreateFolderRequest) error {
	internalPath := UCloudToInternalWithDrive(request.Drive, request.Path)
	err := os.MkdirAll(internalPath, 0770)
	if err != nil {
		return &util.HttpError{
			StatusCode: http.StatusNotFound,
			Why:        "Could not create directory!",
		}
	}

	return nil
}

func processMoveTask(task *FileTask) error {
	request := task.MoveRequest

	sourcePath := UCloudToInternalWithDrive(request.OldDrive, request.OldPath)
	destPath := UCloudToInternalWithDrive(request.NewDrive, request.NewPath)

	if request.Policy == orc.WriteConflictPolicyRename {
		newPath, err := findAvailableNameOnRename(destPath)
		destPath = newPath

		if err != nil {
			log.Error(err.Error())
			return err
		}
	}

	err := os.Rename(sourcePath, destPath)
	if err != nil {
		parentPath := util.Parent(destPath)
		parentStat, err := os.Stat(parentPath)
		log.Info("path = %v stat=%v err=%v", parentPath, parentStat, err)
		if err != nil {
			return &util.HttpError{
				StatusCode: http.StatusNotFound,
				Why:        "Unable to move file. Could not find destination!",
			}
		} else if !parentStat.IsDir() {
			return &util.HttpError{
				StatusCode: http.StatusNotFound,
				Why:        "Unable to move file. Destination is not a directory!",
			}
		}

		_, err = os.Stat(destPath)
		if err == nil {
			return &util.HttpError{
				StatusCode: http.StatusNotFound,
				Why:        "Unable to move file. Destination already exists!",
			}
		}

		return &util.HttpError{
			StatusCode: http.StatusNotFound,
			Why:        "Unable to move file.",
		}
	}

	return nil
}

func moveFiles(request ctrl.MoveFileRequest) error {
	task := FileTask{
		Type:        FileTaskTypeMove,
		Title:       "Moving files...",
		DriveId:     request.OldDrive.Id,
		Timestamp:   fnd.Timestamp(time.Now()),
		MoveRequest: request,
	}

	return task.process(true)
}

func copyFiles(request ctrl.CopyFileRequest) error {
	task := FileTask{
		Type:        FileTaskTypeCopy,
		Title:       "Copying files...",
		DriveId:     request.OldDrive.Id,
		Timestamp:   fnd.Timestamp(time.Now()),
		CopyRequest: request,
	}

	return task.process(true)
}

func moveToTrash(request ctrl.MoveToTrashRequest) error {
	task := FileTask{
		Type:               FileTaskTypeMoveToTrash,
		Title:              "Moving files to trash...",
		DriveId:            request.Drive.Id,
		Timestamp:          fnd.Timestamp(time.Now()),
		MoveToTrashRequest: request,
	}

	return task.process(true)
}

func emptyTrash(request ctrl.EmptyTrashRequest) error {
	driveId := strings.Split(request.Path, "/")[1]

	task := FileTask{
		Type:              FileTaskTypeEmptyTrash,
		Title:             "Emptying trash...",
		DriveId:           driveId,
		Timestamp:         fnd.Timestamp(time.Now()),
		EmptyTrashRequest: request,
	}

	return task.process(true)
}

func (t *FileTask) process(doCreate bool) error {
	if doCreate {
		RegisterTask(t)
	}

	var err error

	go func() {
		switch t.Type {
		case FileTaskTypeCopy:
			err = processCopyTask(t)
			MarkTaskAsComplete(t.DriveId, t.Id)
		case FileTaskTypeMove:
			err = processMoveTask(t)
			MarkTaskAsComplete(t.DriveId, t.Id)
		case FileTaskTypeMoveToTrash:
			err = processMoveToTrash(t)
			MarkTaskAsComplete(t.DriveId, t.Id)
		case FileTaskTypeEmptyTrash:
			err = processEmptyTrash(t)
			MarkTaskAsComplete(t.DriveId, t.Id)
		}
	}()

	return err
}

func processEmptyTrash(task *FileTask) error {
	drive, _ := RetrieveDrive(task.DriveId)
	trashLocation := UCloudToInternalWithDrive(drive, task.EmptyTrashRequest.Path)

	trashEntries, _ := os.ReadDir(trashLocation)

	for _, entry := range trashEntries {
		path := fmt.Sprintf("%s/%s", trashLocation, entry.Name())
		os.RemoveAll(path)
	}

	return nil
}

func processMoveToTrash(task *FileTask) error {
	expectedTrashLocation := UCloudToInternalWithDrive(
		task.MoveToTrashRequest.Drive,
		fmt.Sprintf("/%s/Trash", task.MoveToTrashRequest.Drive.Id),
	)

	if exists, _ := os.Stat(expectedTrashLocation); exists == nil {
		os.Mkdir(expectedTrashLocation, 0770)
	}

	newPath, _ := InternalToUCloud(
		fmt.Sprintf("%s/%s", expectedTrashLocation, util.FileName(task.MoveToTrashRequest.Path)),
	)

	moveTask := FileTask{
		Type:    FileTaskTypeMove,
		Title:   task.Title,
		DriveId: task.DriveId,
		MoveRequest: ctrl.MoveFileRequest{
			OldDrive: task.MoveToTrashRequest.Drive,
			NewDrive: task.MoveToTrashRequest.Drive,
			OldPath:  task.MoveToTrashRequest.Path,
			NewPath:  newPath,
			Policy:   orc.WriteConflictPolicyRename,
		},
	}

	processMoveTask(&moveTask)

	return nil
}

func copyFolder(sourcePath string, destPath string) error {
	sourceFile, _ := os.Open(sourcePath)

	err := os.Mkdir(destPath, 0770)
	if err != nil {
		return err
	}

	dirEntries, _ := sourceFile.ReadDir(0)
	for _, entry := range dirEntries {
		if entry.IsDir() {
			newSource := fmt.Sprintf("%s/%s", sourcePath, entry.Name())
			newDest := fmt.Sprintf("%s/%s", destPath, entry.Name())
			copyFolder(newSource, newDest)
		} else {
			newSource := fmt.Sprintf("%s/%s", sourcePath, entry.Name())
			newDest := fmt.Sprintf("%s/%s", destPath, entry.Name())

			source, _ := os.Open(newSource)
			dest, _ := os.Create(newDest)
			_, err := io.Copy(dest, source)

			if err != nil {
				return err
			}
		}
	}

	return nil
}

func processCopyTask(task *FileTask) error {
	request := task.CopyRequest

	sourcePath := UCloudToInternalWithDrive(request.OldDrive, request.OldPath)
	destPath := UCloudToInternalWithDrive(request.NewDrive, request.NewPath)

	if request.Policy == orc.WriteConflictPolicyRename {
		newPath, err := findAvailableNameOnRename(destPath)
		destPath = newPath

		if err != nil {
			log.Error(err.Error())
			return err
		}
	}

	stat, _ := os.Stat(sourcePath)

	err := func() error {
		if stat.IsDir() {
			return copyFolder(sourcePath, destPath)
		} else {
			source, _ := os.Open(sourcePath)
			dest, _ := os.Create(destPath)
			_, err := io.Copy(dest, source)

			return err
		}
	}()

	if err != nil {
		parentPath := util.Parent(destPath)
		parentStat, err := os.Stat(parentPath)
		if err != nil {
			return &util.HttpError{
				StatusCode: http.StatusNotFound,
				Why:        "Unable to copy file. Could not find destination!",
			}
		} else if !parentStat.IsDir() {
			return &util.HttpError{
				StatusCode: http.StatusNotFound,
				Why:        "Unable to copy file. Destination is not a directory!",
			}
		}

		_, err = os.Stat(destPath)
		if err == nil {
			return &util.HttpError{
				StatusCode: http.StatusNotFound,
				Why:        "Unable to copy file. Destination already exists!",
			}
		}

		return &util.HttpError{
			StatusCode: http.StatusNotFound,
			Why:        "Unable to copy file.",
		}
	}

	return nil

}

func readMetadata(internalPath string, stat os.FileInfo, file *orc.ProviderFile, drive orc.Drive) {
	file.Status.Type = orc.FileTypeFile
	if stat.IsDir() {
		file.Status.Type = orc.FileTypeDirectory
	}

	file.Id = InternalToUCloudWithDrive(drive, internalPath)
	file.Status.Icon = orc.FileIconHintNone
	fileName := util.FileName(internalPath)
	switch fileName {
	case "Trash":
		file.Status.Icon = orc.FileIconHintDirectoryTrash
	case "Jobs":
		fallthrough
	case "UCloud Jobs":
		file.Status.Icon = orc.FileIconHintDirectoryJobs
	}

	file.CreatedAt = FileModTime(stat)
	file.Status.ModifiedAt = FileModTime(stat)
	file.Status.AccessedAt = FileAccessTime(stat)

	file.Status.SizeInBytes = stat.Size()
	file.Status.SizeIncludingChildrenInBytes = stat.Size()

	file.Status.UnixOwner = FileUid(stat)
	file.Status.UnixGroup = FileGid(stat)
	file.Status.UnixMode = int(stat.Mode()) & 0777 // only keep permissions bits
}

func validateDownloadAndOpenFile(session ctrl.DownloadSession) (*os.File, os.FileInfo, error) {
	internalPath := UCloudToInternalWithDrive(session.Drive, session.Path)
	file, err := os.Open(internalPath)
	if err != nil {
		util.SilentClose(file)
		return nil, nil, &util.HttpError{
			StatusCode: http.StatusNotFound,
			Why:        "Could not find file!",
		}
	}

	stat, err := file.Stat()
	if err != nil {
		util.SilentClose(file)
		return nil, nil, &util.HttpError{
			StatusCode: http.StatusNotFound,
			Why:        "Could not find file!",
		}
	}
	if stat.IsDir() {
		util.SilentClose(file)
		return nil, nil, &util.HttpError{
			StatusCode: http.StatusNotFound,
			Why:        "Unable to download directories!",
		}
	}
	return file, stat, nil
}

func createDownload(session ctrl.DownloadSession) error {
	file, _, err := validateDownloadAndOpenFile(session)
	if err != nil {
		return err
	} else {
		util.SilentClose(file)
		return nil
	}
}

func createUpload(request ctrl.CreateUploadRequest) (ctrl.UploadSession, error) {
	path := UCloudToInternalWithDrive(request.Drive, request.Path)

	if request.ConflictPolicy == orc.WriteConflictPolicyRename && request.Type != ctrl.UploadTypeFolder {
		path, _ = findAvailableNameOnRename(path)
	}

	data, err := json.Marshal(
		UploadSessionData{
			Path:           path,
			ConflictPolicy: orc.WriteConflictPolicyRename,
			Type:           request.Type,
		},
	)

	if err != nil {
		log.Error("Unable to marshal upload session: %v", err)
		return ctrl.UploadSession{}, &util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        "Unable to start upload",
		}
	}

	session := ctrl.UploadSession{
		Id:   util.RandomToken(32),
		Data: data,
	}

	return session, nil
}

func uploadWsFile(socket *websocket.Conn, session UploadSessionData) error {
	var fileOffset int64 = 0
	var fileSize int64 = 0

	file, err := os.OpenFile(session.Path, os.O_CREATE+os.O_RDWR, 0644)

	if err != nil {
		log.Error("Failed to open file for upload: %v", err)
		return &util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        "Unable to upload file",
		}
	}

	for {
		messageType, data, readErr := socket.ReadMessage()

		if readErr != nil {
			log.Error("Failed to read message from socket: %v", readErr)
			return &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Error occured while uploading file",
			}
		}

		message := string(data)

		if messageType == websocket.TextMessage {
			fileInfo := strings.Split(message, " ")

			var err error
			fileOffset, _ = strconv.ParseInt(fileInfo[0], 10, 64)
			fileSize, err = strconv.ParseInt(fileInfo[1], 10, 64)

			if err != nil {
				log.Error("Failed to read file information used for upload: %v", err)
				return &util.HttpError{
					StatusCode: http.StatusBadRequest,
					Why:        "Error occured while uploading file",
				}
			}
		} else {
			written, err := file.WriteAt(data, fileOffset)
			if err != nil {
				log.Error("Failed to write to file during upload: %v", err)
				return &util.HttpError{
					StatusCode: http.StatusBadRequest,
					Why:        "Error occured while uploading file",
				}
			}

			fileOffset += int64(written)
			socket.WriteMessage(websocket.TextMessage, []byte(fmt.Sprint(fileOffset)))
		}

		if fileOffset >= fileSize {
			return nil
		}
	}
}

func uploadWsFolder(socket *websocket.Conn, session UploadSessionData) error {
	listing := make(map[uint32]FileListingEntry)
	responseBuffer := bytes.NewBuffer([]byte{})

	drive, _ := ResolveDriveByPath(session.Path)

	log.Info("sessionPath: %s, drive: %s", session.Path, drive.Id)

	flushResponses := func() {
		if responseBuffer.Len() > 0 {
			socket.WriteMessage(websocket.BinaryMessage, responseBuffer.Bytes())
		}
		responseBuffer.Reset()
		log.Info("Flushed responses")
	}

	var backlog []int

	var filesCompleted atomic.Int32

	// Write FilesCompleted messages to the socket.
	go func() {
		var last int32 = 0
		buf := bytes.NewBuffer([]byte{})
		for {
			current := filesCompleted.Load()
			if last != current {
				buf.Reset()
				binary.Write(buf, binary.BigEndian, byte(FolderUploadMessageTypeFilesCompleted.Int()))
				buf = bytes.NewBuffer(binary.BigEndian.AppendUint32(buf.Bytes(), uint32(current)))
				socket.WriteMessage(websocket.BinaryMessage, buf.Bytes())
				log.Info("Writing %v", buf)
				last = current
			}
			time.Sleep(100 * time.Millisecond)
		}
	}()

	for {
		messageType, data, readErr := socket.ReadMessage()
		buffer := bytes.NewBuffer(data)
		log.Info("Messagetype is %v", messageType)

		if readErr != nil {
			log.Error("Failed to read message from socket: %v", readErr)
			return &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Error occured while uploading folder",
			}
		}

		uploadMessageType, _ := buffer.ReadByte()

		log.Info("UploadMessageType: %v", uploadMessageType)

		switch FolderUploadMessageType(uploadMessageType) {
		case FolderUploadMessageTypeListing:
			{
				type ListingResponse struct {
					Message FolderUploadMessageType
					Entry   uint
				}

				workChannel := make(chan []FileListingEntry)
				workResponses := make(chan []ListingResponse)

				// Determine which files needs to be uploaded.
				go func() {
					for {
						entries, more := <-workChannel

						if more {
							log.Info("Got entries %d", len(entries))
							var responses []ListingResponse

							for _, entry := range entries {
								message := FolderUploadMessageTypeOk
								log.Info("Stat on file %s", session.Path+"/"+entry.Path)
								fileInfo, err := os.Stat(session.Path + "/" + entry.Path)

								if err != nil {
									log.Info("file was not found")
									message = FolderUploadMessageTypeOk
								} else {
									log.Info("Actual: %d %d", fileInfo.Size(), fileInfo.ModTime().UnixMilli())
									log.Info("Expected: %d %d", entry.Size, entry.ModifiedAt.UnixMilli())
									diffModifiedAt := entry.ModifiedAt.UnixMilli() - fileInfo.ModTime().UnixMilli()
									if diffModifiedAt < 0 {
										diffModifiedAt = diffModifiedAt * -1
									}

									if fileInfo.IsDir() {
										message = FolderUploadMessageTypeSkip
									} else if uint64(fileInfo.Size()) == entry.Size {
										message = FolderUploadMessageTypeSkip
									} else if diffModifiedAt < 1000 {
										message = FolderUploadMessageTypeSkip
									}
								}

								if message == FolderUploadMessageTypeSkip {
									entry.Offset = entry.Size
									filesCompleted.Add(1)
								}

								responses = append(responses, ListingResponse{Message: message, Entry: uint(entry.Id)})
							}

							workResponses <- responses
						} else {
							log.Info("Got no entries")
							break
						}
					}
					close(workChannel)
				}()

				// Process listing
				go func() {
					var batch []FileListingEntry

					for {
						if buffer.Len() < 1 {
							break
						}
						fileId := binary.BigEndian.Uint32(buffer.Next(4))
						log.Info("FileId is %v", fileId)
						size := binary.BigEndian.Uint64(buffer.Next(8))
						log.Info("size is %v", size)
						modifiedAt := binary.BigEndian.Uint64(buffer.Next(8))
						log.Info("ModifiedAt is %v", modifiedAt)
						pathSize := binary.BigEndian.Uint32(buffer.Next(4))
						log.Info("pathSize is %v", pathSize)

						if pathSize > 1024*64 {
							log.Info("Refusing to allocate space for this file: %d", pathSize)
						}

						path := string(buffer.Next(int(pathSize)))
						log.Info("path is %v", path)

						fileListingEntry := FileListingEntry{
							Id:         uint(fileId),
							Path:       path,
							Size:       size,
							ModifiedAt: fnd.TimeFromUnixMilli(modifiedAt).Time(),
							Offset:     0,
						}

						batch = append(batch, fileListingEntry)
						listing[fileId] = fileListingEntry

						if len(batch) > 100 {
							workChannel <- batch
							batch = []FileListingEntry{}
						}
					}

					if len(batch) > 0 {
						workChannel <- batch
					}

					log.Info("Done processing listing")
				}()

				go func() {
					for {
						batch := <-workResponses

						log.Info("batch is: %v", batch)

						for _, entry := range batch {
							if responseBuffer.Len() < 64 {
								flushResponses()
							}
							if entry.Message == FolderUploadMessageTypeOk {
								backlog = append(backlog, int(entry.Entry))
							}

							binary.Write(responseBuffer, binary.BigEndian, byte(entry.Message.Int()))
							responseBuffer = bytes.NewBuffer(binary.BigEndian.AppendUint32(responseBuffer.Bytes(), uint32(entry.Entry)))
						}

						flushResponses()
					}
				}()
			}
		case FolderUploadMessageTypeSkip:
			{
				log.Info("Skip received")
				fileId := binary.BigEndian.Uint32(buffer.Next(4))
				filesCompleted.Add(1)

				// Remove from backlog
				var newBacklog []int
				for _, entry := range backlog {
					if entry != int(fileId) {
						newBacklog = append(newBacklog, entry)
					}
				}

				backlog = newBacklog
			}
		case FolderUploadMessageTypeChunk:
			{
				fileId := binary.BigEndian.Uint32(buffer.Next(4))
				chunk := buffer.Bytes()

				log.Info("Chunk received for file %d", fileId)

				go func() {
					fileEntry := listing[fileId]

					isDone, err := doHandleFolderUpload(
						session,
						listing[fileId],
						drive,
						chunk,
					)

					if err != nil {
						log.Info("doHandleFolderUpload failed: %v", err)
						return
					}

					fileEntry.Offset += uint64(len(data))
					if isDone || fileEntry.Offset >= fileEntry.Size {
						filesCompleted.Add(1)

						var newBacklog []int

						for _, backlogEntry := range backlog {
							if backlogEntry != int(fileEntry.Id) {
								newBacklog = append(newBacklog, backlogEntry)
							}
						}

						backlog = newBacklog
					}
				}()
			}
		default:
			{
				log.Info("Something else received")
			}
		}
	}

	log.Info("Done here")

	return nil
}

func doHandleFolderUpload(session UploadSessionData, entry FileListingEntry, drive orc.Drive, data []byte) (bool, error) {
	// Create folders
	folders := strings.Split(entry.Path, "/")
	folders = folders[0 : len(folders)-1]
	var allFolders []string

	for i := 0; i < len(folders); i++ {
		// TODO This really needs to be improved
		element := strings.Join(folders[0:i+1], "/")

		if strings.Contains(element, "../") {
			return false, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Bailing",
			}
		}

		if element == ".." {
			return false, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Bailing",
			}
		}

		allFolders = append(allFolders, element)
		i++
	}

	for _, folder := range allFolders {
		createFolder(ctrl.CreateFolderRequest{
			Path:           InternalToUCloudWithDrive(drive, session.Path+"/"+folder),
			Drive:          drive,
			ConflictPolicy: orc.WriteConflictPolicyReject,
		})
	}

	targetPath := session.Path + "/" + entry.Path

	file := uploadDescriptors.get(targetPath, int64(entry.Offset), false, entry.ModifiedAt)

	written, err := file.Handle.Write(data)

	if entry.Offset+uint64(written) >= entry.Size {
		uploadDescriptors.close(*file, orc.WriteConflictPolicyReplace, entry.ModifiedAt)
	}

	file.release()

	if err != nil {
		return false, err
	}

	return true, nil
}

func uploadWs(upload ctrl.UploadDataWs) error {
	defer upload.Socket.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))

	var session UploadSessionData
	err := json.Unmarshal(upload.Session.Data, &session)

	if err != nil {
		log.Error("Unmarshal of session data failed: %v", err)
		return &util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        "Invalid upload session",
		}
	}

	if session.Type == ctrl.UploadTypeFile {
		return uploadWsFile(upload.Socket, session)
	} else {
		return uploadWsFolder(upload.Socket, session)
	}
}

func findAvailableNameOnRename(path string) (string, error) {
	if found, _ := os.Stat(path); found == nil {
		return path, nil
	}

	parent := util.Parent(path)
	fileName := util.FileName(path)
	extensionIndex := strings.LastIndex(fileName, ".")
	newExtension := ""

	if extensionIndex > 0 {
		extension := fileName[extensionIndex:]
		fileName = fileName[:extensionIndex]
		newExtension = extension
	}

	for i := 1; i < 10000; i++ {
		newFilename := fmt.Sprintf("%s(%d)", fileName, i)
		newPath := fmt.Sprintf("%s/%s%s", parent, newFilename, newExtension)

		file, _ := os.Stat(newPath)

		if file == nil {
			return newPath, nil
		}
	}

	return "", &util.HttpError{
		StatusCode: http.StatusBadRequest,
		Why:        fmt.Sprintf("Not able to rename file: %s", path),
	}
}

func download(session ctrl.DownloadSession) (io.ReadSeekCloser, int64, error) {
	file, stat, err := validateDownloadAndOpenFile(session)
	if err != nil {
		return nil, 0, err
	}

	return file, stat.Size(), nil
}

// Upload File Descriptors
type UploadDescriptors struct {
	openDescriptors []UploadDescriptor
	mutex           sync.Mutex
}

type UploadDescriptor struct {
	PartialPath string
	TargetPath  string
	Handle      *os.File
	LastUsed    time.Time
	InUse       *sync.Mutex
	Waiting     *atomic.Int32
}

// Releases the upload file descriptor for further writing.
func (ud *UploadDescriptor) release() {
	ud.InUse.Unlock()
	ud.LastUsed = time.Now()
}

func (descriptors *UploadDescriptors) close(descriptor UploadDescriptor, conflictPolicy orc.WriteConflictPolicy, modifiedAt time.Time) {
	resolvedTargetPath := descriptor.TargetPath

	if conflictPolicy == orc.WriteConflictPolicyRename {
		resolvedTargetPath, _ = findAvailableNameOnRename(descriptor.TargetPath)
	}

	os.Rename(descriptor.PartialPath, resolvedTargetPath)

	if modifiedAt.UnixMilli() > 0 {
		os.Chtimes(resolvedTargetPath, modifiedAt, modifiedAt)
	}

	log.Info("Closing fd: %s %s", descriptor.TargetPath, fmt.Sprint(descriptor.Handle.Fd()))

	descriptors.mutex.Lock()
	descriptor.Handle.Close()

	var newDescriptors []UploadDescriptor
	for _, openDesc := range descriptors.openDescriptors {
		if descriptor.Handle.Fd() != openDesc.Handle.Fd() {
			newDescriptors = append(newDescriptors, openDesc)
		}
	}

	descriptors.openDescriptors = newDescriptors

	descriptors.mutex.Unlock()
}

func (descriptors *UploadDescriptors) get(path string, offset int64, truncate bool, modifiedAt time.Time) *UploadDescriptor {
	descriptors.mutex.Lock()
	internalTargetPath, _ := UCloudToInternal(path)
	internalPartialPath, _ := UCloudToInternal(path + ".ucloud_part")

	var found *UploadDescriptor = nil

	for key, ud := range descriptors.openDescriptors {
		if ud.PartialPath == internalPartialPath {
			found = &descriptors.openDescriptors[key]
		}
	}

	if found != nil {
		descriptors.mutex.Unlock()
		log.Info("Found existing fd: %s %s", found.TargetPath, fmt.Sprint(found.Handle.Fd()))
		return found
	}

	var flags int = os.O_CREATE + os.O_RDWR
	if truncate {
		flags += os.O_TRUNC
	}

	handle, _ := os.OpenFile(internalPartialPath, flags, 0644)

	if modifiedAt.UnixMilli() == 0 {
		os.Chtimes(internalPartialPath, modifiedAt, modifiedAt)
	}

	newDescriptor := UploadDescriptor{
		PartialPath: internalPartialPath,
		TargetPath:  internalTargetPath,
		Handle:      handle,
		LastUsed:    time.Now(),
		InUse:       &sync.Mutex{},
		Waiting:     &atomic.Int32{},
	}
	log.Info("Opened new fd: %s %s", newDescriptor.TargetPath, fmt.Sprint(newDescriptor.Handle.Fd()))

	descriptors.openDescriptors = append(descriptors.openDescriptors, newDescriptor)

	descriptor := &descriptors.openDescriptors[len(descriptors.openDescriptors)-1]

	descriptors.mutex.Unlock()

	descriptor.Waiting.Add(1)
	descriptor.InUse.Lock()
	descriptor.Waiting.Add(-1)
	if offset >= 0 {
		descriptor.Handle.Seek(offset, 0)
	}

	return descriptor
}

// Starts the monitoring loop of upload descriptors, which will periodically close unused file descriptors used for uploads.
func (d *UploadDescriptors) startMonitoringLoop() {
	go func() {
		for {
			d.mutex.Lock()
			var closedDescriptors []uintptr

			for _, descriptor := range d.openDescriptors {
				if time.Now().UnixMilli() > descriptor.LastUsed.UnixMilli()+10000 && descriptor.InUse.TryLock() {
					if descriptor.Waiting.Load() != 0 {
						descriptor.InUse.Unlock()
					} else {
						descriptor.Handle.Close()
						closedDescriptors = append(closedDescriptors, descriptor.Handle.Fd())
					}
				}
			}

			// Remove closed descriptors
			var newDescriptors []UploadDescriptor

			for _, desc := range d.openDescriptors {
				shouldRemove := false
				for _, closed := range closedDescriptors {
					if desc.Handle.Fd() == closed {
						shouldRemove = true
						break
					}
				}

				if !shouldRemove {
					newDescriptors = append(newDescriptors, desc)
				}
			}

			d.openDescriptors = newDescriptors

			d.mutex.Unlock()
			time.Sleep(2 * time.Second)
		}
	}()
}

func UnitToBytes(unit string) uint64 {
	switch unit {
	case "KB":
		return 1000
	case "KiB":
		return 1024
	case "MB":
		return 1000 * 1000
	case "MiB":
		return 1024 * 1024
	case "GB":
		return 1000 * 1000 * 1000
	case "GiB":
		return 1024 * 1024 * 1024
	case "TB":
		return 1000 * 1000 * 1000 * 1000
	case "TiB":
		return 1024 * 1024 * 1024 * 1024
	case "PB":
		return 1000 * 1000 * 1000 * 1000 * 1000
	case "PiB":
		return 1024 * 1024 * 1024 * 1024 * 1024
	default:
		log.Warn("%v Unknown unit %v", util.GetCaller(), unit)
		return 1
	}
}
