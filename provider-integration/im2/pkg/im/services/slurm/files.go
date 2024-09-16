package slurm

import (
	"context"
	"encoding/json"
	"fmt"
	"golang.org/x/sys/unix"
	"io"
	"math"
	"net/http"
	"os"
	"os/user"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/controller/upload"

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

func InitializeFiles() ctrl.FileService {
	browseCache = lru.NewLRU[string, []cachedDirEntry](256, nil, 5*time.Minute)
	loadStorageProducts()
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
		Download:              download,
		RetrieveProducts:      retrieveProducts,
		Transfer:              transfer,
		Uploader:              &uploaderFileSystem{},
	}
}

// TODO put this in the database
var transferSessions = util.NewCache[string, ctrl.FilesTransferRequestInitiateSource](48 * time.Hour)

func transfer(request ctrl.FilesTransferRequest) (ctrl.FilesTransferResponse, error) {
	switch request.Type {
	case ctrl.FilesTransferRequestTypeInitiateSource:
		req := request.InitiateSource()
		token := util.RandomToken(32)
		transferSessions.Set(token, *req)

		return ctrl.FilesTransferResponseInitiateSource(token, []string{"built-in"}), nil

	case ctrl.FilesTransferRequestTypeInitiateDestination:
		req := request.InitiateDestination()
		if !slices.Contains(req.SupportedProtocols, "built-in") {
			return ctrl.FilesTransferResponse{}, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Unable to fulfill this request, no overlap in protocols.",
			}
		}

		localDestinationPath, ok := UCloudToInternal(req.DestinationPath)
		if !ok {
			return ctrl.FilesTransferResponse{}, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Unable to fulfill this request, unknown destination supplied.",
			}
		}

		finfo, err := os.Stat(filepath.Dir(localDestinationPath))
		if err != nil {
			return ctrl.FilesTransferResponse{}, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Unable to fulfill this request, unknown destination supplied.",
			}
		}

		if !probablyHasPermissionToWrite(finfo) {
			return ctrl.FilesTransferResponse{}, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Unable to fulfill this request, you are not allowed to write to the destination folder.",
			}
		}

		_, target := ctrl.CreateFolderUpload(req.DestinationPath, orc.WriteConflictPolicyMergeRename, []byte(localDestinationPath))

		if req.SourceProvider == cfg.Provider.Id {
			// NOTE(Dan): This only happens during local development

			target = strings.Replace(target, cfg.Provider.Hosts.SelfPublic.ToWebSocketUrl(), cfg.Provider.Hosts.Self.ToWebSocketUrl(), 1)
		}

		return ctrl.FilesTransferResponseInitiateDestination(
			"built-in",
			ctrl.TransferBuiltInParameters{
				Endpoint: target,
			},
		), nil

	case ctrl.FilesTransferRequestTypeStart:
		req := request.Start()

		if req.SelectedProtocol != "built-in" {
			return ctrl.FilesTransferResponse{}, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Protocol is not supported",
			}
		}

		session, ok := transferSessions.GetNow(req.Session)
		if !ok {
			return ctrl.FilesTransferResponse{}, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Unknown session",
			}
		}

		var parameters ctrl.TransferBuiltInParameters

		err := json.Unmarshal(req.ProtocolParameters, &parameters)
		if err != nil {
			return ctrl.FilesTransferResponse{}, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Malformed request from UCloud/Core",
			}
		}

		uploadSession := upload.ClientSession{
			Endpoint:       parameters.Endpoint,
			ConflictPolicy: orc.WriteConflictPolicyMergeRename,
			Path:           session.SourcePath,
		}

		internalSource, ok := UCloudToInternal(session.SourcePath)
		if !ok {
			return ctrl.FilesTransferResponse{}, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Unable to open source file",
			}
		}

		rootFile, err := os.Open(internalSource)
		if err != nil {
			return ctrl.FilesTransferResponse{}, &util.HttpError{
				StatusCode: http.StatusBadRequest,
				Why:        "Unable to open source file",
			}
		}

		go upload.ProcessClient(uploadSession, &uploaderClientFile{
			Path: "",
			File: rootFile,
		})

		return ctrl.FilesTransferResponseStart(), nil

	default:
		return ctrl.FilesTransferResponse{}, &util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        "Unknown message",
		}
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
	aLower := strings.ToLower(a.absPath)
	bLower := strings.ToLower(b.absPath)
	return strings.Compare(aLower, bLower)
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

var (
	cachedUser, _ = user.Current()
	cachedGroups  = (func() []string {
		if cachedUser == nil {
			return nil
		} else {
			groupIds, _ := cachedUser.GroupIds()
			return groupIds
		}
	})()
)

func probablyHasPermissionToRead(stat os.FileInfo) bool {
	mode := stat.Mode()
	sys := stat.Sys().(*syscall.Stat_t)

	if mode&0004 != 0 {
		return true
	}

	if cachedUser != nil && cachedUser.Uid == strconv.Itoa(int(sys.Uid)) {
		return mode&0400 != 0
	}

	if cachedGroups != nil {
		for _, gid := range cachedGroups {
			if gid == strconv.Itoa(int(sys.Gid)) {
				return mode&0040 != 0
			}
		}
	}

	return false
}

func probablyHasPermissionToWrite(stat os.FileInfo) bool {
	mode := stat.Mode()
	sys := stat.Sys().(*syscall.Stat_t)

	if mode&0002 != 0 {
		return true
	}

	if cachedUser != nil && cachedUser.Uid == strconv.Itoa(int(sys.Uid)) {
		return mode&0200 != 0
	}

	if cachedGroups != nil {
		for _, gid := range cachedGroups {
			if gid == strconv.Itoa(int(sys.Gid)) {
				return mode&0020 != 0
			}
		}
	}

	return false
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
	shouldFilterByPermissions := request.Flags.FilterHiddenFiles && cfg.Services.Unmanaged && len(util.Components(request.Path)) == 1

	itemIdx := 0
	i := offset
	for i < len(fileList) && itemIdx < request.ItemsPerPage {
		item := &items[itemIdx]
		entry := &fileList[i]
		i++

		base := filepath.Base(entry.absPath)
		if request.Flags.FilterHiddenFiles && strings.HasPrefix(base, ".") {
			continue
		}

		if !entry.hasInfo && !entry.skip {
			stat, err := os.Stat(entry.absPath)
			if err == nil {
				if shouldFilterByPermissions && !probablyHasPermissionToRead(stat) {
					continue
				}

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

	file.Status.SizeInBytes = util.OptValue(stat.Size())

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

func createUpload(request ctrl.CreateUploadRequest) ([]byte, error) {
	// TODO(Dan): This can fail if we do not have permissions to write at this path
	path := UCloudToInternalWithDrive(request.Drive, request.Path)

	if request.ConflictPolicy == orc.WriteConflictPolicyRename && request.Type != ctrl.UploadTypeFolder {
		path, _ = findAvailableNameOnRename(path)
	}
	return []byte(path), nil
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

var StorageProducts []apm.ProductV2
var storageSupport []orc.FSSupport

func loadStorageProducts() {
	for categoryName, category := range ServiceConfig.FileSystems {
		productCategory := apm.ProductCategory{
			Name:                categoryName,
			Provider:            cfg.Provider.Id,
			ProductType:         apm.ProductTypeCompute,
			AccountingFrequency: apm.AccountingFrequencyPeriodicMinute,
			FreeToUse:           false,
			AllowSubAllocations: true,
		}

		usePrice := false
		switch category.Payment.Type {
		case cfg.PaymentTypeMoney:
			productCategory.AccountingUnit.Name = category.Payment.Currency
			productCategory.AccountingUnit.NamePlural = category.Payment.Currency
			productCategory.AccountingUnit.DisplayFrequencySuffix = false
			productCategory.AccountingUnit.FloatingPoint = true
			usePrice = true

		case cfg.PaymentTypeResource:
			productCategory.AccountingUnit.Name = category.Payment.Unit
			productCategory.AccountingUnit.NamePlural = productCategory.AccountingUnit.Name
			productCategory.AccountingUnit.FloatingPoint = false
			productCategory.AccountingUnit.DisplayFrequencySuffix = true
		}

		switch category.Payment.Interval {
		case cfg.PaymentIntervalMinutely:
			productCategory.AccountingFrequency = apm.AccountingFrequencyPeriodicMinute
		case cfg.PaymentIntervalHourly:
			productCategory.AccountingFrequency = apm.AccountingFrequencyPeriodicHour
		case cfg.PaymentIntervalDaily:
			productCategory.AccountingFrequency = apm.AccountingFrequencyPeriodicDay
		default:
			productCategory.AccountingFrequency = apm.AccountingFrequencyOnce
			productCategory.AccountingUnit.DisplayFrequencySuffix = false
		}

		product := apm.ProductV2{
			Type:        apm.ProductTypeCStorage,
			Category:    productCategory,
			Name:        categoryName,
			Description: "A storage product", // TODO

			ProductType:               apm.ProductTypeStorage,
			Price:                     int64(math.Floor(category.Payment.Price * 1_000_000)),
			HiddenInGrantApplications: false,
		}

		if !usePrice {
			product.Price = 1
		}

		StorageProducts = append(StorageProducts, product)
	}

	for _, p := range StorageProducts {
		support := orc.FSSupport{
			Product: apm.ProductReference{
				Id:       p.Name,
				Category: p.Category.Name,
				Provider: cfg.Provider.Id,
			},
		}

		support.Stats.SizeInBytes = true
		support.Stats.ModifiedAt = true
		support.Stats.CreatedAt = true
		support.Stats.AccessedAt = true
		support.Stats.UnixPermissions = true
		support.Stats.UnixOwner = true
		support.Stats.UnixGroup = true

		support.Collection.AclModifiable = false
		support.Collection.UsersCanCreate = false
		support.Collection.UsersCanDelete = false
		support.Collection.UsersCanRename = false

		support.Files.AclModifiable = false
		support.Files.TrashSupport = true
		support.Files.IsReadOnly = false
		support.Files.SearchSupported = false
		support.Files.StreamingSearchSupported = true
		support.Files.SharesSupported = false

		storageSupport = append(storageSupport, support)
	}
}

func retrieveProducts() []orc.FSSupport {
	return storageSupport
}

type uploaderFileSystem struct{}
type uploaderFile struct {
	File     *os.File
	Metadata upload.FileMetadata
	err      error
}

func (u *uploaderFileSystem) OpenFileIfNeeded(session upload.ServerSession, fileMeta upload.FileMetadata) upload.ServerFile {
	rootPath := string(session.UserData)
	internalPath := filepath.Join(rootPath, fileMeta.InternalPath)

	info, err := os.Stat(internalPath)
	if err != nil {
		err = os.MkdirAll(filepath.Dir(internalPath), 0770)
		if err != nil {
			return nil
		}
	} else {
		if info.Size() == fileMeta.Size && math.Abs(info.ModTime().Sub(fileMeta.ModifiedAt.Time()).Minutes()) < 1 {
			return nil
		}
	}

	file, err := os.OpenFile(internalPath, os.O_RDWR|os.O_TRUNC|os.O_CREATE, 0660)
	if err != nil {
		return nil
	}

	return &uploaderFile{file, fileMeta, nil}
}

func (u *uploaderFile) Write(_ context.Context, data []byte) {
	if u.err != nil {
		return
	}

	_, err := u.File.Write(data)

	if err != nil {
		u.err = err
	}
}

func (u *uploaderFile) Close() {
	if u.err == nil {
		t := u.Metadata.ModifiedAt.Time()
		_ = os.Chtimes(u.File.Name(), t, t)
	}
	util.SilentClose(u.File)
}

type uploaderClientFile struct {
	Path string
	File *os.File
}

func (u *uploaderClientFile) ListChildren(ctx context.Context) []string {
	names, err := u.File.Readdirnames(0)
	if err != nil {
		return nil
	}
	return names
}

func (u *uploaderClientFile) OpenChild(ctx context.Context, name string) (upload.FileMetadata, upload.ClientFile) {
	file, err := FileOpenAt(u.File, name, unix.O_RDONLY|unix.O_NOFOLLOW, 0)
	if err != nil {
		return upload.FileMetadata{}, nil
	}

	finfo, err := file.Stat()
	if err != nil {
		return upload.FileMetadata{}, nil
	}

	ftype := upload.FileTypeFile
	if finfo.IsDir() {
		ftype = upload.FileTypeDirectory
	} else if !finfo.Mode().IsRegular() {
		_ = file.Close()
		return upload.FileMetadata{}, nil
	}

	metadata := upload.FileMetadata{
		Size:         finfo.Size(),
		ModifiedAt:   fnd.Timestamp(finfo.ModTime()),
		InternalPath: filepath.Join(u.Path, name),
		Type:         ftype,
	}

	child := &uploaderClientFile{
		Path: metadata.InternalPath,
		File: file,
	}

	return metadata, child
}

func (u *uploaderClientFile) Read(ctx context.Context, target []byte) (int, bool) {
	n, err := u.File.Read(target)
	if err != nil {
		return 0, true
	}

	return n, n == 0
}

func (u *uploaderClientFile) Close() {
	_ = u.File.Close()
}
