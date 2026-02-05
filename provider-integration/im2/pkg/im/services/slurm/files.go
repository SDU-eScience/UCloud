package slurm

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"math"
	"net/http"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"syscall"
	"time"

	"ucloud.dk/pkg/im/external/user"

	"golang.org/x/sys/unix"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/controller/upload"
	apm "ucloud.dk/shared/pkg/accounting"

	lru "github.com/hashicorp/golang-lru/v2/expirable"
	ctrl "ucloud.dk/pkg/im/controller"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

var browseCache *lru.LRU[string, []cachedDirEntry]

func InitializeFiles() ctrl.FileService {
	browseCache = lru.NewLRU[string, []cachedDirEntry](256, nil, 5*time.Minute)
	loadStorageProducts()
	return ctrl.FileService{
		BrowseFiles:                 browse,
		RetrieveFile:                retrieve,
		CreateFolder:                createFolder,
		Move:                        moveFiles,
		Copy:                        copyFiles,
		MoveToTrash:                 moveToTrash,
		EmptyTrash:                  emptyTrash,
		CreateDownloadSession:       createDownload,
		CreateUploadSession:         createUpload,
		Download:                    download,
		RetrieveProducts:            retrieveProducts,
		TransferSourceInitiate:      transferSourceInitiate,
		TransferDestinationInitiate: transferDestinationInitiate,
		TransferSourceBegin:         transferSourceBegin,
		Search:                      search,
		Uploader:                    &uploaderFileSystem{},
	}
}

func search(ctx context.Context, query, folder string, flags orc.FileFlags, output chan orc.ProviderFile) {
	initialFolder, ok := UCloudToInternal(folder)
	driveId, ok2 := DriveIdFromUCloudPath(folder)
	defer close(output)

	if !ok || !ok2 {
		return
	}

	drive, ok := RetrieveDrive(driveId)
	if !ok {
		return
	}

	normalizedQuery := strings.ToLower(query)

	_ = filepath.WalkDir(initialFolder, func(path string, d fs.DirEntry, err error) error {
		select {
		case <-ctx.Done():
			return filepath.SkipAll
		default:
			normalizedName := strings.ToLower(d.Name())
			if strings.Contains(normalizedName, normalizedQuery) {
				stat, err := d.Info()
				if err == nil {
					var result orc.ProviderFile
					readMetadata(path, stat, &result, drive)

					select {
					case <-ctx.Done():
						return filepath.SkipAll
					case output <- result:
						// Do nothing
					}
				}
			}

			return nil
		}
	})
}

func transferSourceInitiate(request orc.FilesProviderTransferRequestInitiateSource) ([]string, *util.HttpError) {
	return []string{"built-in"}, nil
}

func transferDestinationInitiate(request orc.FilesProviderTransferRequestInitiateDestination) (orc.FilesProviderTransferResponse, *util.HttpError) {
	if !slices.Contains(request.SupportedProtocols, "built-in") {
		return orc.FilesProviderTransferResponse{}, &util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        "Unable to fulfill this request, no overlap in protocols.",
		}
	}

	localDestinationPath, ok := UCloudToInternal(request.DestinationPath)
	if !ok {
		return orc.FilesProviderTransferResponse{}, &util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        "Unable to fulfill this request, unknown destination supplied.",
		}
	}

	finfo, err := os.Stat(filepath.Dir(localDestinationPath))
	if err != nil {
		return orc.FilesProviderTransferResponse{}, &util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        "Unable to fulfill this request, unknown destination supplied.",
		}
	}

	if !probablyHasPermissionToWrite(finfo) {
		return orc.FilesProviderTransferResponse{}, &util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        "Unable to fulfill this request, you are not allowed to write to the destination folder.",
		}
	}

	_, target := ctrl.CreateFolderUpload(request.DestinationPath, orc.WriteConflictPolicyMergeRename, localDestinationPath)

	if request.SourceProvider == cfg.Provider.Id {
		// NOTE(Dan): This only happens during local development

		target = strings.Replace(target, cfg.Provider.Hosts.SelfPublic.ToWebSocketUrl(), cfg.Provider.Hosts.Self.ToWebSocketUrl(), 1)
	}

	_ = RegisterTask(TaskInfoSpecification{
		Type:          FileTaskTransferDestination,
		MoreInfo:      util.OptValue(target),
		HasUCloudTask: false,
	})

	resp := ctrl.FilesTransferResponseInitiateDestination(
		"built-in",
		ctrl.TransferBuiltInParameters{
			Endpoint: target,
		},
	)
	return resp, nil
}

func transferSourceBegin(request orc.FilesProviderTransferRequestStart, session ctrl.TransferSession) *util.HttpError {
	err := RegisterTask(TaskInfoSpecification{
		Type:          FileTaskTransfer,
		UCloudSource:  util.OptValue[string](session.SourcePath),
		MoreInfo:      util.OptValue[string](session.Id),
		HasUCloudTask: true,
		Icon:          "heroPaperAirplane",
	})

	return err
}

func processTransferTask(task *TaskInfo) TaskProcessingResult {
	sessionId := task.MoreInfo.Value
	session, ok := ctrl.RetrieveTransferSession(sessionId)
	if !ok {
		return TaskProcessingResult{
			Error:           fmt.Errorf("unknown file transfer session, it might have expired"),
			AllowReschedule: false,
		}
	}

	var parameters ctrl.TransferBuiltInParameters

	err := json.Unmarshal(session.ProtocolParameters, &parameters)
	if err != nil {
		return TaskProcessingResult{
			Error:           fmt.Errorf("malformed request"),
			AllowReschedule: false,
		}
	}

	uploadSession := upload.ClientSession{
		Endpoint:       parameters.Endpoint,
		ConflictPolicy: orc.WriteConflictPolicyMergeRename,
		Path:           session.SourcePath,
	}

	internalSource, ok := UCloudToInternal(session.SourcePath)
	if !ok {
		return TaskProcessingResult{
			Error:           fmt.Errorf("unable to open source file"),
			AllowReschedule: false,
		}
	}

	const numberOfAttempts = 10
	var uploadErr error = nil
	for i := 0; i < numberOfAttempts; i++ {
		// NOTE(Dan): The rootFile is automatically closed by the uploader
		rootFile, err := os.Open(internalSource)
		if err != nil {
			return TaskProcessingResult{
				Error:           fmt.Errorf("unable to open source file"),
				AllowReschedule: false,
			}
		}

		uploaderRoot := &uploaderClientFile{
			Path: "",
			File: rootFile,
		}

		finfo, err := rootFile.Stat()
		if err != nil {
			return TaskProcessingResult{
				Error:           fmt.Errorf("unable to open source file"),
				AllowReschedule: false,
			}
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

		cancelChannel := make(chan util.Empty)
		go func() {
			for !task.Done.Load() {
				newState := task.UserRequestedState.Load()
				if newState != nil {
					cancelChannel <- util.Empty{}
					break
				}

				time.Sleep(1 * time.Second)
			}
		}()

		report := upload.ProcessClient(uploadSession, uploaderRoot, rootMetadata, &task.Status, cancelChannel)

		if report.WasCancelledByUser {
			uploadErr = nil
			break
		}

		if !report.NormalExit {
			uploadErr = fmt.Errorf("an abnormal error occured during the transfer process")
			break
		}

		if report.BytesTransferred == 0 && report.NewFilesUploaded == 0 {
			uploadErr = nil
			break
		}

		if i == numberOfAttempts-1 {
			uploadErr = fmt.Errorf("unable to upload all files")
		}
	}

	if uploadErr == nil {
		if !upload.CloseSessionFromClient(uploadSession) {
			return TaskProcessingResult{
				Error:           fmt.Errorf("failed to close upload session"),
				AllowReschedule: true,
			}
		}
	}

	return TaskProcessingResult{
		Error:           uploadErr,
		AllowReschedule: true,
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
	cachedUser, cachedUserErr = user.Current()
	cachedGroups              = (func() []string {
		if cachedUserErr != nil {
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

	if os.Getuid() == int(sys.Uid) && mode&0400 != 0 {
		return true
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

	if os.Getuid() == int(sys.Uid) && mode&0200 != 0 {
		return true
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

func browse(request orc.FilesProviderBrowseRequest) (fnd.PageV2[orc.ProviderFile], *util.HttpError) {
	internalPath := UCloudToInternalWithDrive(request.ResolvedCollection, request.Browse.Flags.Path.Value)
	sortBy := request.Browse.SortBy.Value

	fileList, ok := browseCache.Get(internalPath)
	if !ok || request.Browse.Next.Value == "" {
		// NOTE(Dan): Never perform caching on the initial page. This way, if a user refreshes a page they will always
		// get up-to-date results. The caching is mostly meant to deal with extremely large folders (e.g. more than
		// 1 million files). When dealing with large folders, the readdir syscall ends up taking a very significant
		// amount of time, often significantly more than the combined time of calling stat on the files in a single
		// page.
		file, err := os.Open(internalPath)
		defer util.SilentClose(file)

		if err != nil {
			if len(util.Components(request.Browse.Flags.Path.Value)) == 1 {
				go func() {
					time.Sleep(1 * time.Second)
					os.Exit(0)
				}()

				return fnd.EmptyPage[orc.ProviderFile](), &util.HttpError{
					StatusCode: http.StatusNotFound,
					Why:        "Unable to open project directory. Try again in a few seconds...",
				}
			}

			return fnd.EmptyPage[orc.ProviderFile](), &util.HttpError{
				StatusCode: http.StatusNotFound,
				Why:        "Could not find directory: " + internalPath,
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
		if request.Browse.SortDirection.Value == orc.SortDirectionDescending {
			slices.Reverse(entries)
		}

		fileList = entries
		browseCache.Add(internalPath, entries)
	}

	offset := 0
	if request.Browse.Next.Value != "" {
		converted, err := strconv.ParseInt(request.Browse.Next.Value, 10, 64)
		if err != nil {
			offset = int(converted)
		}
	}

	if offset >= len(fileList) || offset < 0 {
		return fnd.EmptyPage[orc.ProviderFile](), nil
	}

	items := make([]orc.ProviderFile, min(request.Browse.ItemsPerPage, len(fileList)-offset))
	shouldFilterByPermissions := request.Browse.Flags.FilterHiddenFiles.Value && cfg.Services.Unmanaged && len(util.Components(request.Browse.Flags.Path.Value)) == 1

	itemIdx := 0
	i := offset
	for i < len(fileList) && itemIdx < request.Browse.ItemsPerPage {
		item := &items[itemIdx]
		entry := &fileList[i]
		i++

		base := filepath.Base(entry.absPath)
		if request.Browse.Flags.FilterHiddenFiles.Value && strings.HasPrefix(base, ".") {
			continue
		}

		if !entry.hasInfo && !entry.skip {
			stat, err := os.Stat(entry.absPath)
			if err == nil {
				entry.hasInfo = true
				entry.info = stat
			}
		}

		if entry.hasInfo && shouldFilterByPermissions && !probablyHasPermissionToRead(entry.info) {
			continue
		}

		if !entry.hasInfo {
			continue
		} else {
			itemIdx += 1
		}

		readMetadata(entry.absPath, entry.info, item, request.ResolvedCollection)
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

func retrieve(request orc.FilesProviderRetrieveRequest) (orc.ProviderFile, *util.HttpError) {
	var result orc.ProviderFile
	internalPath := UCloudToInternalWithDrive(request.ResolvedCollection, request.Retrieve.Id)
	stat, err := os.Stat(internalPath)
	if err != nil {
		return result, &util.HttpError{
			StatusCode: http.StatusNotFound,
			Why:        "Could not find file!",
		}
	}

	readMetadata(internalPath, stat, &result, request.ResolvedCollection)
	return result, nil
}

func createFolder(request orc.FilesProviderCreateFolderRequest) *util.HttpError {
	internalPath := UCloudToInternalWithDrive(request.ResolvedCollection, request.Id)
	err := os.MkdirAll(internalPath, 0770)
	if err != nil {
		return &util.HttpError{
			StatusCode: http.StatusNotFound,
			Why:        "Could not create directory!",
		}
	}

	return nil
}

func doMoveFiles(ucloudSource, ucloudDest string, conflictPolicy orc.WriteConflictPolicy) *util.HttpError {
	sourcePath, ok1 := UCloudToInternal(ucloudSource)
	destPath, ok2 := UCloudToInternal(ucloudDest)
	if !ok1 || !ok2 {
		return util.ServerHttpError("Unable to resolve files needed for rename")
	}

	if conflictPolicy == orc.WriteConflictPolicyRename {
		newPath, err := findAvailableNameOnRename(destPath)
		destPath = newPath

		if err != nil {
			return err
		}
	}

	err := os.Rename(sourcePath, destPath)
	if err != nil {
		parentPath := util.Parent(destPath)
		parentStat, err := os.Stat(parentPath)
		log.Info("path = %v stat=%v err=%v", parentPath, parentStat, err)
		if err != nil {
			return util.ServerHttpError("Unable to move file. Could not find destination!")
		} else if !parentStat.IsDir() {
			return util.ServerHttpError("Unable to move file. Destination is not a directory!")
		}

		_, err = os.Stat(destPath)
		if err == nil {
			return util.ServerHttpError("Unable to move file. Destination already exists!")
		}
		return util.ServerHttpError("Unable to move file.")
	}

	return nil
}

func moveFiles(request orc.FilesProviderMoveOrCopyRequest) *util.HttpError {
	return doMoveFiles(request.OldId, request.NewId, request.ConflictPolicy)
}

func emptyTrash(request orc.FilesProviderTrashRequest) *util.HttpError {
	task := TaskInfoSpecification{
		Type:          FileTaskTypeEmptyTrash,
		CreatedAt:     fnd.Timestamp(time.Now()),
		UCloudSource:  util.OptValue(request.Id),
		HasUCloudTask: true,
		Icon:          "trash",
	}

	return RegisterTask(task)
}

func processEmptyTrash(task *TaskInfo) TaskProcessingResult {
	ucloudTrashPath := task.UCloudSource.Value
	trashLocation, ok := UCloudToInternal(ucloudTrashPath)
	if !ok {
		return TaskProcessingResult{
			Error: fmt.Errorf("Unable to resolve trash folder"),
		}
	}

	trashEntries, _ := os.ReadDir(trashLocation)
	for _, entry := range trashEntries {
		path := fmt.Sprintf("%s/%s", trashLocation, entry.Name())

		// NOTE(Dan): Silently ignore trash errors
		_ = os.RemoveAll(path)
	}

	return TaskProcessingResult{}
}

func moveToTrash(request orc.FilesProviderTrashRequest) *util.HttpError {
	if cfg.Services.Unmanaged {
		parents := util.Parents(request.Id)
		root := ""
		for i := 0; i < len(parents); i++ {
			internalPath, ok := UCloudToInternal(parents[i])
			if !ok {
				continue
			}

			fstat, err := os.Stat(internalPath)
			if err != nil || !probablyHasPermissionToWrite(fstat) {
				continue
			}

			root = internalPath
			break
		}

		if root == "" {
			return util.ServerHttpError("Could not resolve trash location")
		}

		expectedTrashLocation := filepath.Join(root, "Trash")
		if stat, _ := os.Stat(expectedTrashLocation); stat == nil {
			err := os.Mkdir(expectedTrashLocation, 0770)
			if err != nil {
				return util.ServerHttpError("Could not create trash folder")
			}
		}

		newPath, ok := InternalToUCloud(fmt.Sprintf("%s/%s", expectedTrashLocation, util.FileName(request.Id)))
		if !ok {
			return util.ServerHttpError("Could not resolve source file")
		}

		return doMoveFiles(request.Id, newPath, orc.WriteConflictPolicyRename)
	} else {
		driveId, _ := DriveIdFromUCloudPath(request.Id)
		expectedTrashLocation, ok := UCloudToInternal(fmt.Sprintf("/%s/Trash", driveId))
		if !ok {
			return util.ServerHttpError("Could not resolve drive location")
		}

		if stat, _ := os.Stat(expectedTrashLocation); stat == nil {
			err := os.Mkdir(expectedTrashLocation, 0770)
			if err != nil {
				return util.ServerHttpError("Could not create trash folder")
			}
		}

		newPath, ok := InternalToUCloud(fmt.Sprintf("%s/%s", expectedTrashLocation, util.FileName(request.Id)))
		if !ok {
			return util.ServerHttpError("Could not resolve source file")
		}

		return doMoveFiles(request.Id, newPath, orc.WriteConflictPolicyRename)
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
	case ServiceConfig.Compute.JobFolderName:
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

func validateDownloadAndOpenFile(session ctrl.DownloadSession) (*os.File, os.FileInfo, *util.HttpError) {
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

func createDownload(session ctrl.DownloadSession) *util.HttpError {
	file, _, err := validateDownloadAndOpenFile(session)
	if err != nil {
		return err
	} else {
		util.SilentClose(file)
		return nil
	}
}

func createUpload(request orc.FilesProviderCreateUploadRequest) (string, *util.HttpError) {
	// TODO(Dan): This can fail if we do not have permissions to write at this path
	path := UCloudToInternalWithDrive(request.ResolvedCollection, request.Id)

	if request.ConflictPolicy == orc.WriteConflictPolicyRename && request.Type != orc.UploadTypeFolder {
		path, _ = findAvailableNameOnRename(path)
	}
	return path, nil
}

func findAvailableNameOnRename(path string) (string, *util.HttpError) {
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

func download(session ctrl.DownloadSession) (io.ReadSeekCloser, int64, *util.HttpError) {
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
			ProductType:         apm.ProductTypeStorage,
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
		support.Files.TrashSupported = true
		support.Files.IsReadOnly = false
		support.Files.SearchSupported = false
		support.Files.StreamingSearchSupported = true
		support.Files.SharesSupported = false
		support.Files.OpenInTerminal = true

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
	rootPath := session.UserData
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

func (u *uploaderFileSystem) OnSessionClose(session upload.ServerSession, success bool) {
	if success {
		tasks := ListActiveTasks()
		for _, task := range tasks {
			if task.Type == FileTaskTransferDestination && task.MoreInfo.Present {
				if strings.Contains(task.MoreInfo.Value, session.Id) {
					_ = PostTaskStatus(TaskStatusUpdate{
						Id:       task.Id,
						NewState: util.OptValue(fnd.TaskStateSuccess),
					})
				}
			}
		}
	}
}

func (u *uploaderFile) Write(_ context.Context, data []byte) error {
	if u.err != nil {
		return u.err
	}

	_, err := u.File.Write(data)

	if err != nil {
		u.err = err
	}
	return u.err
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

func (u *uploaderClientFile) Read(ctx context.Context, target []byte) (int, bool, error) {
	n, err := u.File.Read(target)
	if err != nil {
		return 0, true, err
	}

	return n, n == 0, nil
}

func (u *uploaderClientFile) Close() {
	_ = u.File.Close()
}
