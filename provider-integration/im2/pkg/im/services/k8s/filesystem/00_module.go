package filesystem

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/MichaelTJones/walk"
	"io"
	"math"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"slices"
	"strconv"
	"strings"
	"time"
	"ucloud.dk/pkg/im/controller/fsearch"

	lru "github.com/hashicorp/golang-lru/v2/expirable"
	"golang.org/x/sys/unix"
	"ucloud.dk/pkg/apm"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/controller/upload"
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var storageSupport []orc.FSSupport

var browseCache *lru.LRU[string, []cachedDirEntry]

const SensitivityXattr string = "user.sensitivity"

type cachedDirEntry struct {
	subPath string
	hasInfo bool
	skip    bool
	info    os.FileInfo
}

func InitFiles() ctrl.FileService {
	browseCache = lru.NewLRU[string, []cachedDirEntry](256, nil, 5*time.Minute)
	loadStorageProducts()

	initScanQueue()
	go func() {
		for util.IsAlive {
			time.Sleep(loopMonitoring())
		}
	}()

	return ctrl.FileService{
		BrowseFiles:                 browseFiles,
		RetrieveFile:                retrieveFile,
		CreateFolder:                createFolder,
		Move:                        move,
		Copy:                        copyFiles,
		MoveToTrash:                 moveToTrash,
		EmptyTrash:                  emptyTrash,
		CreateDownloadSession:       createDownload,
		Download:                    download,
		CreateUploadSession:         createUpload,
		RetrieveProducts:            retrieveProducts,
		TransferSourceInitiate:      transferSourceInitiate,
		TransferDestinationInitiate: transferDestinationInitiate,
		TransferSourceBegin:         transferSourceBegin,
		Search:                      search,
		Uploader:                    &uploaderFileSystem{},
		CreateDrive:                 createDrive,
		DeleteDrive:                 deleteDrive,
		RenameDrive:                 renameDrive,
		CreateShare:                 createShare,
	}
}

func createDownload(request ctrl.DownloadSession) error {
	fd, _, err := validateAndOpenFileForDownload(request.Path)
	util.SilentCloseIfOk(fd, err)
	return err
}

func download(request ctrl.DownloadSession) (io.ReadSeekCloser, int64, error) {
	fd, size, err := validateAndOpenFileForDownload(request.Path)
	if err != nil {
		return nil, 0, err
	}

	return fd, size, nil
}

func validateAndOpenFileForDownload(path string) (*os.File, int64, error) {
	drive, subpath, _ := OpenDriveAtUCloudPath(path)
	defer drive.Close()

	fd, err := drive.OpenSubPath(subpath, os.O_RDONLY, 0)
	if err != nil {
		return nil, 0, err
	}

	info, err := fd.Stat()

	if err != nil {
		util.SilentClose(fd)
		return nil, 0, util.UserHttpError("Could not find file!")
	}

	if info.IsDir() {
		util.SilentClose(fd)
		return nil, 0, util.UserHttpError("Unable to download directories!")
	}
	return fd, info.Size(), nil
}

func move(request ctrl.MoveFileRequest) error {
	return doMove(request, false)
}

func doMove(request ctrl.MoveFileRequest, updateTimestamps bool) error {
	conflictPolicy := request.Policy

	sourceDrive, sourceSubPath, ok1 := OpenDriveAtUCloudPath(request.OldPath)
	destDrive, destSubPath, ok2 := OpenDriveAtUCloudPath(request.NewPath)

	defer sourceDrive.Close()
	defer destDrive.Close()

	if !ok1 || !ok2 {
		return util.ServerHttpError("Unable to resolve files needed for rename")
	}

	if sourceDrive.Drive.Id != destDrive.Drive.Id {
		if ctrl.IsResourceLocked(destDrive.Drive.Resource, destDrive.Drive.Specification.Product) {
			return util.PaymentError()
		}
	}

	sourceParent, err1 := sourceDrive.OpenSubPath(sourceSubPath+"/..", 0, 0)
	destParent, err2 := destDrive.OpenSubPath(destSubPath+"/..", 0, 0)
	defer util.SilentCloseIfOk(sourceParent, err1)
	defer util.SilentCloseIfOk(destParent, err2)
	if err1 != nil || err2 != nil {
		return util.UserHttpError("Unable to rename files")
	}

	if conflictPolicy == orc.WriteConflictPolicyRename {
		newPath, err := findAvailableNameOnRename(destDrive, destSubPath)
		destSubPath = newPath

		if err != nil {
			return err
		}
	}

	err := unix.Renameat(
		int(sourceParent.Fd()),
		filepath.Base(sourceSubPath),
		int(destParent.Fd()),
		filepath.Base(destSubPath),
	)

	if updateTimestamps {
		targetFd, err := destDrive.OpenSubPath(destSubPath, unix.O_RDONLY, 0)
		if err == nil {
			t := time.Now()
			utimes := []unix.Timeval{
				unix.NsecToTimeval(t.UnixNano()),
				unix.NsecToTimeval(t.UnixNano()),
			}
			_ = unix.Futimes(int(targetFd.Fd()), utimes)
			util.SilentClose(targetFd)
		}
	}

	if err != nil {
		return util.UserHttpError("Unable to move file.")
	}

	return nil
}

func createFolder(request ctrl.CreateFolderRequest) error {
	if ctrl.IsResourceLocked(request.Drive.Resource, request.Drive.Specification.Product) {
		return util.PaymentError()
	}

	drive, subpath, ok := OpenDriveAtUCloudPath(request.Path)
	defer drive.Close()
	if !ok {
		return util.ServerHttpError("Could not find file")
	}

	return drive.Mkdirs(subpath, 0770)
}

func browseFiles(request ctrl.BrowseFilesRequest) (fnd.PageV2[orc.ProviderFile], error) {
	sortBy := request.SortBy

	drive, subpath, _ := OpenDriveAtUCloudPath(request.Path)
	defer drive.Close()

	fileList, ok := browseCache.Get(request.Path)
	if !ok || request.Next == "" {
		// NOTE(Dan): Never perform caching on the initial page. This way, if a user refreshes a page they will always
		// get up-to-date results. The caching is mostly meant to deal with extremely large folders (e.g. more than
		// 1 million files). When dealing with large folders, the readdir syscall ends up taking a very significant
		// amount of time, often significantly more than the combined time of calling stat on the files in a single
		// page.
		file, err := drive.OpenSubPath(subpath, unix.O_RDONLY, 0)

		if err != nil {
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
			entries[i].subPath = fmt.Sprintf("%v/%v", subpath, fileName)
		}

		if sortBy != "PATH" {
			// We must stat all files immediately to get info about them
			for i := 0; i < len(entries); i++ {
				entry := &entries[i]

				fd, err := drive.OpenSubPath(entry.subPath, unix.O_RDONLY|unix.O_NOFOLLOW, 0)
				if err == nil {
					stat, err := fd.Stat()
					if err == nil {
						entry.info = stat
						entry.hasInfo = true
					}
					util.SilentClose(fd)
				} else {
					// This path will only be hit if the file does not exist or if it is a symlink which points
					// outside the path. If it is a symlink, attempt to get stat info about it for display purposes.
					stat, err := drive.LStatSubPath(entry.subPath)
					if err == nil {
						entry.info = stat
						entry.hasInfo = true
					}
				}

				if !entry.hasInfo {
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
		browseCache.Add(request.Path, entries)
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
		entry := &fileList[i]
		i++

		base := filepath.Base(entry.subPath)
		if request.Flags.FilterHiddenFiles && strings.HasPrefix(base, ".") {
			continue
		}

		if !entry.hasInfo && !entry.skip {
			fd, err := drive.OpenSubPath(entry.subPath, unix.O_RDONLY, 0)
			if err == nil {
				stat, err := fd.Stat()
				if err == nil {
					entry.hasInfo = true
					entry.info = stat
				}
				util.SilentClose(fd)
			} else {
				// This path will only be hit if the file does not exist or if it is a symlink which points
				// outside the path. If it is a symlink, attempt to get stat info about it for display purposes.
				stat, err := drive.LStatSubPath(entry.subPath)
				if err == nil {
					entry.info = stat
					entry.hasInfo = true
				}
			}
		}

		if !entry.hasInfo {
			continue
		} else {
			items[itemIdx] = nativeStat(drive, entry.subPath, entry.info)
			itemIdx += 1
		}
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

func retrieveFile(request ctrl.RetrieveFileRequest) (orc.ProviderFile, error) {
	drive, subpath, ok := OpenDriveAtUCloudPath(request.Path)
	defer drive.Close()

	if !ok {
		return orc.ProviderFile{}, util.UserHttpError("Could not find file: %s", util.FileName(request.Path))
	}

	file, err := drive.OpenSubPath(subpath, unix.O_RDONLY, 0)
	defer util.SilentCloseIfOk(file, err)
	if err != nil {
		return orc.ProviderFile{}, util.UserHttpError("Could not find file: %s", util.FileName(request.Path))
	}

	info, err := file.Stat()
	if err != nil {
		return orc.ProviderFile{}, util.UserHttpError("Could not find file: %s", util.FileName(request.Path))
	}

	return nativeStat(drive, subpath, info), nil
}

func nativeStat(drive OpenedDrive, subpath string, info os.FileInfo) orc.ProviderFile {
	ucloudPath := drive.SubPathToUCloud(subpath)

	result := orc.ProviderFile{
		Id: ucloudPath,
		Status: orc.UFileStatus{
			Type:                         orc.FileTypeFile,
			Icon:                         orc.FileIconHintNone,
			SizeInBytes:                  util.OptValue[int64](0),
			SizeIncludingChildrenInBytes: util.OptNone[int64](),
			ModifiedAt:                   fnd.Timestamp{},
			AccessedAt:                   fnd.Timestamp{},
			UnixMode:                     0,
			UnixOwner:                    DefaultUid,
			UnixGroup:                    DefaultUid,
		},
		CreatedAt:         fnd.Timestamp{},
		LegacySensitivity: getInheritedSensitivity(drive, subpath).Value,
	}

	result.Status.ModifiedAt = FileModTime(info)
	result.Status.AccessedAt = FileAccessTime(info)
	result.Status.SizeInBytes.Set(info.Size())
	result.Status.UnixMode = int(info.Mode() & 0777)
	result.Status.UnixOwner = FileUid(info)
	result.Status.UnixGroup = FileGid(info)
	if info.IsDir() {
		result.Status.Type = orc.FileTypeDirectory

		if strings.HasSuffix(subpath, "/Jobs") {
			result.Status.Icon = orc.FileIconHintDirectoryJobs
		} else if strings.HasSuffix(subpath, "/Trash") {
			result.Status.Icon = orc.FileIconHintDirectoryTrash
		}
	}

	return result
}

func compareFileByPath(a, b cachedDirEntry) int {
	aLower := strings.ToLower(a.subPath)
	bLower := strings.ToLower(b.subPath)
	return strings.Compare(aLower, bLower)
}

func compareFileBySize(a, b cachedDirEntry) int {
	if a.hasInfo && !b.hasInfo {
		return -1
	} else if !a.hasInfo && b.hasInfo {
		return 1
	} else if !a.hasInfo && !b.hasInfo {
		return strings.Compare(a.subPath, b.subPath)
	}

	aSize := a.info.Size()
	bSize := b.info.Size()
	if aSize < bSize {
		return -1
	} else if aSize > bSize {
		return 1
	} else {
		return strings.Compare(a.subPath, b.subPath)
	}
}

func compareFileByModifiedAt(a, b cachedDirEntry) int {
	if a.hasInfo && !b.hasInfo {
		return -1
	} else if !a.hasInfo && b.hasInfo {
		return 1
	} else if !a.hasInfo && !b.hasInfo {
		return strings.Compare(a.subPath, b.subPath)
	}

	aModTime := a.info.ModTime()
	bModTime := b.info.ModTime()
	if aModTime.Before(bModTime) {
		return -1
	} else if aModTime.After(bModTime) {
		return 1
	} else {
		return strings.Compare(a.subPath, b.subPath)
	}
}

func findAvailableNameOnRename(drive OpenedDrive, subpath string) (string, error) {
	fd, err := drive.OpenSubPath(subpath, 0, 0)
	if err != nil {
		return subpath, nil
	}

	util.SilentClose(fd)

	parent := filepath.Dir(subpath)
	fileName := filepath.Base(subpath)
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

		fd, err = drive.OpenSubPath(newPath, 0, 0)
		util.SilentCloseIfOk(fd, err)

		if err != nil {
			return newPath, nil
		}
	}

	return "", &util.HttpError{
		StatusCode: http.StatusBadRequest,
		Why:        fmt.Sprintf("Not able to rename file: %s", subpath),
	}
}

func retrieveProducts() []orc.FSSupport {
	return storageSupport
}

func loadStorageProducts() {
	pc := apm.ProductCategory{
		Name:        shared.ServiceConfig.FileSystem.Name,
		Provider:    cfg.Provider.Id,
		ProductType: apm.ProductTypeStorage,
		AccountingUnit: apm.AccountingUnit{
			Name:                   "GB",
			NamePlural:             "GB",
			FloatingPoint:          false,
			DisplayFrequencySuffix: false,
		},
		AccountingFrequency: apm.AccountingFrequencyOnce,
		FreeToUse:           false,
		AllowSubAllocations: true,
	}

	defaultProduct := apm.ProductV2{
		Type:                      apm.ProductTypeCStorage,
		Category:                  pc,
		Name:                      pc.Name,
		Description:               "A storage product",
		ProductType:               apm.ProductTypeStorage,
		Price:                     1,
		HiddenInGrantApplications: false,
	}

	defaultSupport := orc.FSSupport{
		Product: apm.ProductReference{
			Id:       pc.Name,
			Category: pc.Name,
			Provider: cfg.Provider.Id,
		},
	}

	{
		defaultSupport.Stats.SizeInBytes = true
		defaultSupport.Stats.ModifiedAt = true
		defaultSupport.Stats.CreatedAt = true
		defaultSupport.Stats.AccessedAt = true
		defaultSupport.Stats.UnixPermissions = true
		defaultSupport.Stats.UnixOwner = true
		defaultSupport.Stats.UnixGroup = true

		defaultSupport.Collection.AclModifiable = true
		defaultSupport.Collection.UsersCanCreate = true
		defaultSupport.Collection.UsersCanDelete = true
		defaultSupport.Collection.UsersCanRename = true

		defaultSupport.Files.AclModifiable = false
		defaultSupport.Files.TrashSupport = true
		defaultSupport.Files.IsReadOnly = false
		defaultSupport.Files.SearchSupported = false
		defaultSupport.Files.StreamingSearchSupported = true
		defaultSupport.Files.SharesSupported = true
		defaultSupport.Files.OpenInTerminal = shared.ServiceConfig.Compute.IntegratedTerminal.Enabled
	}

	shareProduct := apm.ProductV2{
		Type:                      apm.ProductTypeCStorage,
		Category:                  pc,
		Name:                      "share",
		Description:               "Used for shared files",
		ProductType:               apm.ProductTypeStorage,
		Price:                     1,
		HiddenInGrantApplications: false,
	}

	shareSupport := defaultSupport
	shareSupport.Product.Id = shareProduct.Name
	shareSupport.Collection.AclModifiable = false
	shareSupport.Collection.UsersCanCreate = false
	shareSupport.Collection.UsersCanCreate = false
	shareSupport.Collection.UsersCanRename = false

	projectHomeProduct := apm.ProductV2{
		Type:                      apm.ProductTypeCStorage,
		Category:                  pc,
		Name:                      "project-home",
		Description:               "Used for member files",
		ProductType:               apm.ProductTypeStorage,
		Price:                     1,
		HiddenInGrantApplications: false,
	}

	projectHomeSupport := shareSupport
	projectHomeSupport.Product.Id = projectHomeProduct.Name

	shared.StorageProducts = []apm.ProductV2{defaultProduct, shareProduct, projectHomeProduct}
	storageSupport = []orc.FSSupport{defaultSupport, shareSupport, projectHomeSupport}
}

func createUpload(request ctrl.CreateUploadRequest) (string, error) {
	var err error
	drive, subpath, ok := OpenDriveAtUCloudPath(request.Path)
	defer drive.Close()

	if !ok {
		return "", util.UserHttpError("Unable to upload a file here")
	}

	if request.ConflictPolicy == orc.WriteConflictPolicyRename && request.Type != ctrl.UploadTypeFolder {
		subpath, err = findAvailableNameOnRename(drive, subpath)
		if err != nil {
			return "", util.UserHttpError("Unable to upload a file here")
		}
	}

	if ctrl.IsResourceLocked(request.Drive.Resource, request.Drive.Specification.Product) {
		return "", util.PaymentError()
	}

	return drive.SubPathToUCloud(subpath), nil
}

type uploaderFileSystem struct{}
type uploaderFile struct {
	File     *os.File
	Metadata upload.FileMetadata
	err      error
}

func (u *uploaderFileSystem) OpenFileIfNeeded(session upload.ServerSession, fileMeta upload.FileMetadata) upload.ServerFile {
	ucloudPath := session.UserData
	drive, rootSubPath, _ := OpenDriveAtUCloudPath(ucloudPath)
	defer drive.Close()

	subPath := filepath.Join(rootSubPath, fileMeta.InternalPath)

	var info os.FileInfo
	fd, err := drive.OpenSubPath(subPath, unix.O_RDONLY, 0)
	if err == nil {
		info, err = fd.Stat()
		util.SilentClose(fd)
	}

	if err != nil {
		err = drive.Mkdirs(filepath.Dir(subPath), 0770)
		if err != nil {
			return nil
		}
	} else {
		if info.Size() == fileMeta.Size && math.Abs(info.ModTime().Sub(fileMeta.ModifiedAt.Time()).Minutes()) < 1 {
			return nil
		}
	}

	file, err := drive.OpenSubPath(subPath, unix.O_RDWR|unix.O_TRUNC|unix.O_CREAT, 0660)
	if err != nil {
		return nil
	}

	return &uploaderFile{file, fileMeta, nil}
}

func (u *uploaderFileSystem) OnSessionClose(session upload.ServerSession, success bool) {
	if success {
		tasks := ListAllActiveTasks()
		for _, task := range tasks {
			if task.Type == FileTaskTransferDestination && task.MoreInfo.Present {
				if strings.Contains(task.MoreInfo.Value, session.Id) {
					_ = PostTaskStatus(task.UCloudUsername, TaskStatusUpdate{
						Id:       task.Id,
						NewState: util.OptValue(orc.TaskStateSuccess),
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

		utimes := []unix.Timeval{
			unix.NsecToTimeval(t.UnixNano()),
			unix.NsecToTimeval(t.UnixNano()),
		}
		_ = unix.Futimes(int(u.File.Fd()), utimes)
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

func moveToTrash(request ctrl.MoveToTrashRequest) error {
	drive, _, ok := OpenDriveAtUCloudPath(request.Path)
	if !ok {
		return util.UserHttpError("Unknown file")
	}

	err := drive.Mkdirs("Trash", 0770)
	if err != nil {
		return util.ServerHttpError("Could not create trash folder")
	}

	newPath := drive.SubPathToUCloud(fmt.Sprintf("Trash/%v", filepath.Base(request.Path)))

	return doMove(ctrl.MoveFileRequest{
		OldDrive: request.Drive,
		NewDrive: request.Drive,
		OldPath:  request.Path,
		NewPath:  newPath,
		Policy:   orc.WriteConflictPolicyRename,
	}, true)
}

func emptyTrash(request ctrl.EmptyTrashRequest) error {
	drive, subpath, ok := OpenDriveAtUCloudPath(request.Path)
	defer drive.Close()
	if !ok {
		return util.UserHttpError("Unable to resolve trash folder")
	}

	err := DoDeleteFile(drive, subpath)
	if err != nil {
		return err
	}

	_ = drive.Mkdirs(subpath, 0770)
	RequestScan(drive.Drive.Id)
	return nil
}

func DoDeleteFile(drive OpenedDrive, subPath string) error {
	parentDir, err1 := drive.OpenSubPath(subPath+"/..", unix.O_RDONLY, 0)
	stagingArea, err2 := os.OpenFile(shared.ServiceConfig.FileSystem.TrashStagingArea, unix.O_RDONLY, 0)
	defer util.SilentCloseIfOk(parentDir, err1)
	defer util.SilentCloseIfOk(stagingArea, err2)

	if err1 != nil || err2 != nil {
		return util.UserHttpError("Unable to resolve trash folder")
	}

	err := unix.Renameat(
		int(parentDir.Fd()),
		filepath.Base(subPath),
		int(stagingArea.Fd()),
		util.RandomToken(16),
	)

	if err != nil {
		return util.UserHttpError("Unable to empty the trash at the moment")
	}

	return nil
}

func transferSourceInitiate(request ctrl.FilesTransferRequestInitiateSource) ([]string, error) {
	return []string{"built-in"}, nil
}

func transferDestinationInitiate(request ctrl.FilesTransferRequestInitiateDestination) (ctrl.FilesTransferResponse, error) {
	if !slices.Contains(request.SupportedProtocols, "built-in") {
		return ctrl.FilesTransferResponse{}, &util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        "Unable to fulfill this request, no overlap in protocols.",
		}
	}

	drive, _, ok := OpenDriveAtUCloudPath(request.DestinationPath)
	defer drive.Close()
	if !ok {
		return ctrl.FilesTransferResponse{}, &util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        "Unable to fulfill this request, unknown destination supplied.",
		}
	}

	if ctrl.IsResourceLocked(drive.Drive.Resource, drive.Drive.Specification.Product) {
		return ctrl.FilesTransferResponse{}, util.PaymentError()
	}

	_, target := ctrl.CreateFolderUpload(request.DestinationPath, orc.WriteConflictPolicyMergeRename, request.DestinationPath)

	if request.SourceProvider == cfg.Provider.Id {
		// NOTE(Dan): This only happens during local development

		target = strings.Replace(target, cfg.Provider.Hosts.SelfPublic.ToWebSocketUrl(), cfg.Provider.Hosts.Self.ToWebSocketUrl(), 1)
	}

	_ = RegisterTask(TaskInfoSpecification{
		Type:          FileTaskTransferDestination,
		MoreInfo:      util.OptValue(target),
		HasUCloudTask: false,
	})

	return ctrl.FilesTransferResponseInitiateDestination(
		"built-in",
		ctrl.TransferBuiltInParameters{
			Endpoint: target,
		},
	), nil

}

func transferSourceBegin(request ctrl.FilesTransferRequestStart, session ctrl.TransferSession) error {
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

	drive, subpath, ok := OpenDriveAtUCloudPath(session.SourcePath)
	defer drive.Close()
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
		rootFile, err := drive.OpenSubPath(subpath, unix.O_RDONLY, 0)
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

func search(ctx context.Context, query, folder string, flags ctrl.FileFlags, output chan orc.ProviderFile) {
	drive, subpath, ok := OpenDriveAtUCloudPath(folder)
	defer drive.Close()
	defer close(output)

	if !ok {
		return
	}

	internalPath := filepath.Join(drive.AbsInternalPath, subpath)

	searchIndex, ok := ctrl.RetrieveSearchIndex(drive.Drive.Id)
	q := fsearch.NewQuery(query)
	_ = walk.Walk(internalPath, runtime.NumCPU(), func(absInternalPath string, info os.FileInfo, err error) error {
		select {
		case <-ctx.Done():
			return fmt.Errorf("context is done")
		default:
		}

		if q.Matches(absInternalPath) {
			driveSubPath, _ := strings.CutPrefix(absInternalPath, drive.AbsInternalPath+"/")
			match := nativeStat(drive, driveSubPath, info)
			output <- match
		}

		if info.IsDir() && !searchIndex.ContinueDown(absInternalPath, q) {
			return walk.SkipDir
		} else {
			return nil
		}
	})
}

func createDrive(drive orc.Drive) error {
	localPath, ok := driveToLocalPath(&drive)
	if !ok {
		return util.ServerHttpError("unknown drive")
	}

	if ctrl.IsResourceLocked(drive.Resource, drive.Specification.Product) {
		return util.PaymentError()
	}

	return os.Mkdir(localPath, 0770)
}

func safeOpenFileLegacy(path string, mode int, perm uint32) (*os.File, bool) {
	// NOTE(Dan): Prefer OpenDrive for most operations

	components := util.Components(path)
	componentsLength := len(components)

	if componentsLength == 0 {
		return nil, false
	}

	fd, err := unix.Open("/"+components[0], unix.O_NOFOLLOW, 0)
	if err != nil {
		return nil, false
	}

	for i := 1; i < componentsLength; i++ {
		opts := unix.O_NOFOLLOW
		thisPerms := uint32(0)
		if i == componentsLength-1 {
			opts |= mode
			thisPerms = perm
		}

		newFd, err := unix.Openat(fd, components[i], opts, thisPerms)
		_ = unix.Close(fd)
		fd = newFd
		if err != nil {
			return nil, false
		}
	}

	if mode&unix.O_CREAT != 0 && (mode&unix.O_WRONLY != 0 || mode&unix.O_RDWR != 0) {
		_ = unix.Fchown(fd, DefaultUid, DefaultUid)
	}

	return os.NewFile(uintptr(fd), components[componentsLength-1]), true
}

func deleteDrive(drive orc.Drive) error {
	internalDrivePath, ok := IReallyNeedUCloudToInternal("/" + drive.Id)
	if !ok {
		return util.ServerHttpError("unknown drive")
	}

	parentPath := filepath.Dir(internalDrivePath)

	parentDir, ok1 := safeOpenFileLegacy(parentPath, unix.O_RDONLY, 0)
	stagingArea, err2 := os.OpenFile(shared.ServiceConfig.FileSystem.TrashStagingArea, unix.O_RDONLY, 0)
	defer util.SilentClose(parentDir)
	defer util.SilentCloseIfOk(stagingArea, err2)

	if !ok1 || err2 != nil {
		return util.UserHttpError("Unable to resolve trash folder")
	}

	err := unix.Renameat(
		int(parentDir.Fd()),
		filepath.Base(internalDrivePath),
		int(stagingArea.Fd()),
		util.RandomToken(16),
	)

	if err != nil {
		return util.UserHttpError("Unable to empty the trash at the moment")
	}

	return nil
}

func renameDrive(_ orc.Drive) error {
	// Nothing to do
	return nil
}

func createShare(share orc.Share) (driveId string, err error) {
	sourcePath := share.Specification.SourceFilePath
	drive, subPath, ok := OpenDriveAtUCloudPath(sourcePath)
	defer drive.Close()
	if !ok {
		return "", util.UserHttpError("File does not exist")
	}

	file, err := drive.OpenSubPath(subPath, unix.O_RDONLY, 0)
	if err != nil {
		return "", util.UserHttpError("File does not exist")
	}
	defer util.SilentClose(file)

	stat, err := file.Stat()
	if stat == nil || err != nil {
		return "", util.UserHttpError("File does not exist")
	}

	if !stat.IsDir() {
		return "", util.UserHttpError("File is not a directory")
	}

	title := filepath.Base(subPath)
	driveId, err = orc.RegisterDrive(orc.ProviderRegisteredResource[orc.DriveSpecification]{
		Spec: orc.DriveSpecification{
			Title: title,
			Product: apm.ProductReference{
				Id:       "share",
				Category: shared.ServiceConfig.FileSystem.Name,
				Provider: cfg.Provider.Id,
			},
		},
		ProviderGeneratedId: util.OptValue(fmt.Sprintf("s-%s", share.Id)),
		CreatedBy:           util.OptValue("_ucloud"),
		Project:             util.OptNone[string](),
		ProjectAllRead:      false,
		ProjectAllWrite:     false,
	})

	return
}

// NOTE(Brian) Function to support inherited sensitivity.
// This is legacy functionality, and thus only implemented for backwards compatibility.
func getInheritedSensitivity(drive OpenedDrive, subPath string) util.Option[string] {
	validSensitivity := []string{"CONFIDENTIAL", "SENSITIVE", "PRIVATE"}

	ancestors := util.Parents(subPath)
	ancestors = append(ancestors, subPath)

	result := ""

	for _, ancestor := range ancestors {
		fd, err := drive.OpenSubPath(ancestor, unix.O_RDONLY, 0)

		if err != nil {
			continue
		}

		// Get extended attributes
		buffer := make([]byte, 64)
		count, err := unix.Fgetxattr(int(fd.Fd()), SensitivityXattr, buffer)
		util.SilentClose(fd)

		if err != nil || count < 1 {
			continue
		}

		value := strings.ToUpper(string(buffer[:count]))
		if value != "" {
			if slices.Contains(validSensitivity, value) {
				result = value
			}
		}
	}

	return util.OptStringIfNotEmpty(result)
}

const DefaultUid = 11042
