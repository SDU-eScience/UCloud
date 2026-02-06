package filesystem

import (
	"context"
	"encoding/json"
	"fmt"
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

	"github.com/MichaelTJones/walk"
	lru "github.com/hashicorp/golang-lru/v2/expirable"
	"golang.org/x/sys/unix"
	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/controller/fsearch"
	upload2 "ucloud.dk/pkg/controller/upload"
	shared2 "ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

var storageSupport []orc.FSSupport

var browseCache *lru.LRU[string, []cachedDirEntry]

const SensitivityXattr string = "user.sensitivity"

type cachedDirEntry struct {
	absPath string
	hasInfo bool
	skip    bool
	info    os.FileInfo
}

func InitFiles() controller.FileService {
	browseCache = lru.NewLRU[string, []cachedDirEntry](256, nil, 5*time.Minute)
	loadStorageProducts()

	initScanQueue()
	go func() {
		for util.IsAlive {
			time.Sleep(loopMonitoring())
		}
	}()

	return controller.FileService{
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

func OpenFile(path string, mode int, perm uint32) (*os.File, bool) {
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

func createDownload(request controller.FileDownloadSession) *util.HttpError {
	if UCloudPathIsSensitive(request.Path) {
		return util.UserHttpError("Downloads are disabled for this project")
	}

	fd, _, err := validateAndOpenFileForDownload(request.Path)
	util.SilentCloseIfOk(fd, err.AsError())
	return err
}

func download(request controller.FileDownloadSession) (io.ReadSeekCloser, int64, *util.HttpError) {
	fd, size, err := validateAndOpenFileForDownload(request.Path)
	if err != nil {
		return nil, 0, err
	}

	return fd, size, nil
}

func validateAndOpenFileForDownload(path string) (*os.File, int64, *util.HttpError) {
	internalPath, ok, _ := UCloudToInternal(path)
	if !ok {
		return nil, 0, util.UserHttpError("Could not find file!")
	}

	fd, ok := OpenFile(internalPath, unix.O_RDONLY, 0)
	if !ok {
		return nil, 0, util.UserHttpError("Could not find file!")
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

func move(request orc.FilesProviderMoveOrCopyRequest) *util.HttpError {
	return doMove(request, false)
}

func doMove(request orc.FilesProviderMoveOrCopyRequest, updateTimestamps bool) *util.HttpError {
	if !AllowUCloudPathsTogether([]string{request.OldId, request.NewId}) {
		return util.ServerHttpError("Some of these files cannot be used together. One or more are sensitive.")
	}

	conflictPolicy := request.ConflictPolicy
	sourcePath, ok1, _ := UCloudToInternal(request.OldId)
	destPath, ok2, destDrive := UCloudToInternal(request.NewId)
	if !ok1 || !ok2 {
		return util.ServerHttpError("Unable to resolve files needed for rename")
	}

	sourceDriveId, _ := DriveIdFromUCloudPath(request.OldId)
	if sourceDriveId != destDrive.Id {
		if controller.ResourceIsLocked(destDrive.Resource, destDrive.Specification.Product) {
			return util.PaymentError()
		}
	}

	if conflictPolicy == orc.WriteConflictPolicyRename {
		newPath, err := findAvailableNameOnRename(destPath)
		destPath = newPath

		if err != nil {
			return err
		}
	}

	sourceParent, ok1 := OpenFile(filepath.Dir(sourcePath), 0, 0)
	destParent, ok2 := OpenFile(filepath.Dir(destPath), 0, 0)
	defer util.SilentClose(sourceParent)
	defer util.SilentClose(destParent)

	if !ok1 || !ok2 {
		return util.UserHttpError("Unable to rename files")
	}

	err := unix.Renameat(
		int(sourceParent.Fd()),
		filepath.Base(sourcePath),
		int(destParent.Fd()),
		filepath.Base(destPath),
	)

	if updateTimestamps {
		targetFd, ok := OpenFile(destPath, unix.O_RDONLY, 0)
		if ok {
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

func DoCreateFolder(internalPath string) *util.HttpError {
	components := util.Components(internalPath)
	componentsLength := len(components)

	if componentsLength == 0 {
		return nil
	}

	fd, err := unix.Open("/"+components[0], unix.O_NOFOLLOW, 0)
	if err != nil {
		return util.UserHttpError("Could not find directory")
	}

	for i := 1; i < componentsLength; i++ {
		err = unix.Mkdirat(fd, components[i], 0770)
		didCreate := err == nil

		opts := unix.O_NOFOLLOW
		thisPerms := uint32(0)

		newFd, err := unix.Openat(fd, components[i], opts, thisPerms)
		if err == nil && didCreate {
			_ = unix.Fchown(newFd, DefaultUid, DefaultUid)
		}
		_ = unix.Close(fd)
		fd = newFd
		if err != nil {
			return util.UserHttpError("Failed to create directory")
		}
	}
	_ = unix.Close(fd)
	return nil
}

func createFolder(request orc.FilesProviderCreateFolderRequest) *util.HttpError {
	internalPath, ok, destDrive := UCloudToInternal(request.Id)
	if !ok {
		return util.UserHttpError("Could not find file")
	}

	if controller.ResourceIsLocked(destDrive.Resource, request.ResolvedCollection.Specification.Product) {
		return util.PaymentError()
	}

	return DoCreateFolder(internalPath)
}

func browseFiles(request orc.FilesProviderBrowseRequest) (fnd.PageV2[orc.ProviderFile], *util.HttpError) {
	internalPath, ok, _ := UCloudToInternal(request.Browse.Flags.Path.Value)
	sortBy := request.Browse.Flags.SortBy.Value

	if !ok {
		return fnd.EmptyPage[orc.ProviderFile](), util.UserHttpError("Could not find directory")
	}

	fileList, ok := browseCache.Get(internalPath)
	if !ok || !request.Browse.Next.Present {
		// NOTE(Dan): Never perform caching on the initial page. This way, if a user refreshes a page they will always
		// get up-to-date results. The caching is mostly meant to deal with extremely large folders (e.g. more than
		// 1 million files). When dealing with large folders, the readdir syscall ends up taking a very significant
		// amount of time, often significantly more than the combined time of calling stat on the files in a single
		// page.
		file, ok := OpenFile(internalPath, unix.O_RDONLY, 0)
		defer util.SilentClose(file)

		if !ok {
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

				fd, ok := OpenFile(entry.absPath, unix.O_RDONLY, 0)
				stat, err := fd.Stat()
				util.SilentClose(fd)

				if ok && err == nil {
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
	if request.Browse.Next.Present {
		converted, err := strconv.ParseInt(request.Browse.Next.Value, 10, 64)
		if err == nil {
			offset = int(converted)
		}
	}

	if offset >= len(fileList) || offset < 0 {
		return fnd.EmptyPage[orc.ProviderFile](), nil
	}

	items := make([]orc.ProviderFile, min(request.Browse.ItemsPerPage, len(fileList)-offset))

	itemIdx := 0
	i := offset
	for i < len(fileList) && itemIdx < request.Browse.ItemsPerPage {
		entry := &fileList[i]
		i++

		base := filepath.Base(entry.absPath)
		if request.Browse.Flags.FilterHiddenFiles.Value && strings.HasPrefix(base, ".") {
			continue
		}

		if !entry.hasInfo && !entry.skip {
			fd, ok := OpenFile(entry.absPath, unix.O_RDONLY, 0)
			stat, err := fd.Stat()
			util.SilentClose(fd)

			if ok && err == nil {
				entry.hasInfo = true
				entry.info = stat
			}
		}

		if !entry.hasInfo {
			continue
		} else {
			items[itemIdx] = nativeStat(&request.ResolvedCollection, entry.absPath, entry.info)
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

func retrieveFile(request orc.FilesProviderRetrieveRequest) (orc.ProviderFile, *util.HttpError) {
	internalPath, ok, _ := UCloudToInternal(request.Retrieve.Id)
	if !ok {
		return orc.ProviderFile{}, util.UserHttpError("Could not find file: %s", util.FileName(request.Retrieve.Id))
	}

	file, ok := OpenFile(internalPath, unix.O_RDONLY, 0)
	defer util.SilentClose(file)
	if !ok {
		return orc.ProviderFile{}, util.UserHttpError("Could not find file: %s", util.FileName(request.Retrieve.Id))
	}

	info, err := file.Stat()
	if err != nil {
		return orc.ProviderFile{}, util.UserHttpError("Could not find file: %s", util.FileName(request.Retrieve.Id))
	}

	return nativeStat(&request.ResolvedCollection, internalPath, info), nil
}

func nativeStat(drive *orc.Drive, internalPath string, info os.FileInfo) orc.ProviderFile {
	ucloudPath, ok := InternalToUCloudWithDrive(drive, internalPath)
	if !ok {
		ucloudPath = util.FileName(internalPath)
	}

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
		LegacySensitivity: getInheritedSensitivity(drive, internalPath).Value,
	}

	result.Status.ModifiedAt = FileModTime(info)
	result.CreatedAt = result.Status.ModifiedAt
	result.Status.AccessedAt = FileAccessTime(info)
	result.Status.SizeInBytes.Set(info.Size())
	result.Status.UnixMode = int(info.Mode() & 0777)
	result.Status.UnixOwner = FileUid(info)
	result.Status.UnixGroup = FileGid(info)
	if info.IsDir() {
		result.Status.Type = orc.FileTypeDirectory

		if strings.HasSuffix(internalPath, "/Jobs") {
			result.Status.Icon = orc.FileIconHintDirectoryJobs
		} else if strings.HasSuffix(internalPath, "/Trash") {
			result.Status.Icon = orc.FileIconHintDirectoryTrash
		}
	}

	return result
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

func findAvailableNameOnRename(path string) (string, *util.HttpError) {
	fd, ok := OpenFile(path, 0, 0)
	util.SilentClose(fd)
	if !ok {
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

		fd, ok = OpenFile(newPath, 0, 0)
		util.SilentClose(fd)

		if !ok {
			return newPath, nil
		}
	}

	return "", &util.HttpError{
		StatusCode: http.StatusBadRequest,
		Why:        fmt.Sprintf("Not able to rename file: %s", path),
	}
}

func retrieveProducts() []orc.FSSupport {
	return storageSupport
}

func loadStorageProducts() {
	pc := apm.ProductCategory{
		Name:        shared2.ServiceConfig.FileSystem.Name,
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
		defaultSupport.Files.TrashSupported = true
		defaultSupport.Files.IsReadOnly = false
		defaultSupport.Files.SearchSupported = false
		defaultSupport.Files.StreamingSearchSupported = true
		defaultSupport.Files.SharesSupported = true
		defaultSupport.Files.OpenInTerminal = shared2.ServiceConfig.Compute.IntegratedTerminal.Enabled
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

	shared2.StorageProducts = []apm.ProductV2{defaultProduct, shareProduct, projectHomeProduct}
	storageSupport = []orc.FSSupport{defaultSupport, shareSupport, projectHomeSupport}
}

func createUpload(request orc.FilesProviderCreateUploadRequest) (string, *util.HttpError) {
	path, ok, destDrive := UCloudToInternal(request.Id)
	if !ok {
		return "", util.UserHttpError("Unable to upload a file here, unable to find parent folder")
	}

	if request.ConflictPolicy == orc.WriteConflictPolicyRename && request.Type != orc.UploadTypeFolder {
		path, _ = findAvailableNameOnRename(path)
	}

	if controller.ResourceIsLocked(destDrive.Resource, request.ResolvedCollection.Specification.Product) {
		return "", util.PaymentError()
	}

	return path, nil
}

type uploaderFileSystem struct{}
type uploaderFile struct {
	File     *os.File
	Metadata upload2.FileMetadata
	err      error
}

func (u *uploaderFileSystem) OpenFileIfNeeded(session upload2.ServerSession, fileMeta upload2.FileMetadata) upload2.ServerFile {
	rootPath := session.UserData
	internalPath := filepath.Join(rootPath, fileMeta.InternalPath)

	fd, ok := OpenFile(internalPath, unix.O_RDONLY, 0)
	info, err := fd.Stat()
	util.SilentClose(fd)
	if !ok || err != nil {
		err = DoCreateFolder(filepath.Dir(internalPath)).AsError()
		if err != nil {
			return nil
		}
	} else {
		if info.Size() == fileMeta.Size && math.Abs(info.ModTime().Sub(fileMeta.ModifiedAt.Time()).Minutes()) < 1 {
			return nil
		}
	}

	file, ok := OpenFile(internalPath, unix.O_RDWR|unix.O_TRUNC|unix.O_CREAT, 0660)
	if !ok {
		return nil
	}

	return &uploaderFile{file, fileMeta, nil}
}

func (u *uploaderFileSystem) OnSessionClose(session upload2.ServerSession, success bool) {
	if success {
		tasks := ListAllActiveTasks()
		for _, task := range tasks {
			if task.Type == FileTaskTransferDestination && task.MoreInfo.Present {
				if strings.Contains(task.MoreInfo.Value, session.Id) {
					_ = PostTaskStatus(task.UCloudUsername, TaskStatusUpdate{
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

func (u *uploaderClientFile) OpenChild(ctx context.Context, name string) (upload2.FileMetadata, upload2.ClientFile) {
	file, err := FileOpenAt(u.File, name, unix.O_RDONLY|unix.O_NOFOLLOW, 0)
	if err != nil {
		return upload2.FileMetadata{}, nil
	}

	finfo, err := file.Stat()
	if err != nil {
		return upload2.FileMetadata{}, nil
	}

	ftype := upload2.FileTypeFile
	if finfo.IsDir() {
		ftype = upload2.FileTypeDirectory
	} else if !finfo.Mode().IsRegular() {
		_ = file.Close()
		return upload2.FileMetadata{}, nil
	}

	metadata := upload2.FileMetadata{
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

func moveToTrash(request orc.FilesProviderTrashRequest) *util.HttpError {
	driveId, _ := DriveIdFromUCloudPath(request.Id)
	expectedTrashLocation, ok, _ := UCloudToInternal(fmt.Sprintf("/%s/Trash", driveId))
	if !ok {
		return util.ServerHttpError("Could not resolve drive location")
	}

	err := DoCreateFolder(expectedTrashLocation)
	if err != nil {
		return util.ServerHttpError("Could not create trash folder")
	}

	newPath, ok := InternalToUCloudWithDrive(
		&request.ResolvedCollection,
		fmt.Sprintf("%s/%s", expectedTrashLocation, util.FileName(request.Id)),
	)

	if !ok {
		return util.ServerHttpError("unknown drive")
	}

	return doMove(orc.FilesProviderMoveOrCopyRequest{
		ResolvedOldCollection: request.ResolvedCollection,
		ResolvedNewCollection: request.ResolvedCollection,
		OldId:                 request.Id,
		NewId:                 newPath,
		ConflictPolicy:        orc.WriteConflictPolicyRename,
	}, true)
}

func emptyTrash(request orc.FilesProviderTrashRequest) *util.HttpError {
	ucloudTrashPath := request.Id
	trashLocation, ok, _ := UCloudToInternal(ucloudTrashPath)
	if !ok {
		return util.UserHttpError("Unable to resolve trash folder")
	}

	err := DoDeleteFile(trashLocation)
	if err != nil {
		return err
	}

	_ = DoCreateFolder(trashLocation)

	path, ok := DriveIdFromUCloudPath(request.Id)
	if ok {
		RequestScan(path)
	}

	return nil
}

func DoDeleteFile(internalPath string) *util.HttpError {
	parentDir, ok1 := OpenFile(filepath.Dir(internalPath), unix.O_RDONLY, 0)
	stagingArea, ok2 := OpenFile(shared2.ServiceConfig.FileSystem.TrashStagingArea, unix.O_RDONLY, 0)
	defer util.SilentClose(parentDir)
	defer util.SilentClose(stagingArea)
	if !ok1 || !ok2 {
		return util.UserHttpError("Unable to resolve trash folder")
	}

	err := unix.Renameat(
		int(parentDir.Fd()),
		filepath.Base(internalPath),
		int(stagingArea.Fd()),
		util.RandomToken(16),
	)

	if err != nil {
		return util.UserHttpError("Unable to empty the trash at the moment")
	}

	return nil
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

	localDestinationPath, ok, drive := UCloudToInternal(request.DestinationPath)
	if !ok {
		return orc.FilesProviderTransferResponse{}, &util.HttpError{
			StatusCode: http.StatusBadRequest,
			Why:        "Unable to fulfill this request, unknown destination supplied.",
		}
	}

	if controller.ResourceIsLocked(drive.Resource, drive.Specification.Product) {
		return orc.FilesProviderTransferResponse{}, util.PaymentError()
	}

	_, target := controller.FileCreateFolderUpload(request.DestinationPath, orc.WriteConflictPolicyMergeRename, localDestinationPath)

	if request.SourceProvider == cfg.Provider.Id {
		// NOTE(Dan): This only happens during local development

		target = strings.Replace(target, cfg.Provider.Hosts.SelfPublic.ToWebSocketUrl(), cfg.Provider.Hosts.Self.ToWebSocketUrl(), 1)
	}

	_ = RegisterTask(TaskInfoSpecification{
		Type:          FileTaskTransferDestination,
		MoreInfo:      util.OptValue(target),
		HasUCloudTask: false,
	})

	return controller.FilesTransferResponseInitiateDestination(
		"built-in",
		controller.TransferBuiltInParameters{
			Endpoint: target,
		},
	), nil

}

func transferSourceBegin(request orc.FilesProviderTransferRequestStart, session controller.FileTransferSession) *util.HttpError {
	err := RegisterTask(TaskInfoSpecification{
		Type:           FileTaskTransfer,
		UCloudUsername: session.Username,
		UCloudSource:   util.OptValue[string](session.SourcePath),
		MoreInfo:       util.OptValue[string](session.Id),
		HasUCloudTask:  true,
		Icon:           "heroPaperAirplane",
	})

	return err
}

func processTransferTask(task *TaskInfo) TaskProcessingResult {
	sessionId := task.MoreInfo.Value
	session, ok := controller.FileRetrieveTransferSession(sessionId)
	if !ok {
		return TaskProcessingResult{
			Error:           fmt.Errorf("unknown file transfer session, it might have expired"),
			AllowReschedule: false,
		}
	}

	var parameters controller.TransferBuiltInParameters

	err := json.Unmarshal(session.ProtocolParameters, &parameters)
	if err != nil {
		return TaskProcessingResult{
			Error:           fmt.Errorf("malformed request"),
			AllowReschedule: false,
		}
	}

	uploadSession := upload2.ClientSession{
		Endpoint:       parameters.Endpoint,
		ConflictPolicy: orc.WriteConflictPolicyMergeRename,
		Path:           session.SourcePath,
	}

	internalSource, ok, _ := UCloudToInternal(session.SourcePath)
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
		rootFile, ok := OpenFile(internalSource, unix.O_RDONLY, 0)
		if !ok {
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
		ftype := upload2.FileTypeFile
		if finfo.IsDir() {
			ftype = upload2.FileTypeDirectory
		}
		rootMetadata := upload2.FileMetadata{
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

		report := upload2.ProcessClient(uploadSession, uploaderRoot, rootMetadata, &task.Status, cancelChannel)

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
		if !upload2.CloseSessionFromClient(uploadSession) {
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

func search(ctx context.Context, query, folder string, flags orc.FileFlags, output chan orc.ProviderFile) {
	initialFolder, ok, _ := UCloudToInternal(folder)
	driveId, ok2 := DriveIdFromUCloudPath(folder)
	defer close(output)

	if query == "" {
		return
	}

	if !ok || !ok2 {
		return
	}

	drive, ok := ResolveDrive(driveId)
	if !ok {
		return
	}

	searchIndex, ok := controller.DriveRetrieveSearchIndex(driveId)
	q := fsearch.NewQuery(query)
	_ = walk.Walk(initialFolder, runtime.NumCPU(), func(path string, info os.FileInfo, err error) error {
		if info == nil || err != nil {
			return walk.SkipDir
		}

		if q.Matches(path) {
			match := nativeStat(drive, path, info)
			output <- match
		}

		if info.IsDir() && !searchIndex.ContinueDown(path, q) {
			return walk.SkipDir
		} else {
			return nil
		}
	})
}

func createDrive(drive orc.Drive) *util.HttpError {
	localPath, ok, _ := DriveToLocalPath(&drive)
	if !ok {
		return util.ServerHttpError("unknown drive")
	}

	if controller.ResourceIsLocked(drive.Resource, drive.Specification.Product) {
		return util.PaymentError()
	}

	return DoCreateFolder(localPath)
}

func deleteDrive(drive orc.Drive) *util.HttpError {
	path, ok, _ := DriveToLocalPath(&drive)
	if !ok {
		return util.ServerHttpError("unknown drive")
	}
	reportUsedStorage(drive, 0)
	return DoDeleteFile(path)
}

func renameDrive(_ orc.Drive) *util.HttpError {
	// Nothing to do
	return nil
}

func createShare(share orc.Share) (string, *util.HttpError) {
	sourcePath := share.Specification.SourceFilePath
	sourceInternalPath, ok, _ := UCloudToInternal(sourcePath)
	if !ok {
		return "", util.UserHttpError("File does not exist")
	}

	file, ok := OpenFile(sourceInternalPath, unix.O_RDONLY, 0)
	if !ok {
		return "", util.UserHttpError("File does not exist")
	}
	stat, err := file.Stat()
	if stat == nil || err != nil {
		return "", util.UserHttpError("File does not exist")
	}
	util.SilentClose(file)

	if !stat.IsDir() {
		return "", util.UserHttpError("File is not a directory")
	}

	title := util.FileName(sourceInternalPath)
	driveIdResp, herr := orc.DrivesControlRegister.Invoke(fnd.BulkRequestOf(orc.ProviderRegisteredResource[orc.DriveSpecification]{
		Spec: orc.DriveSpecification{
			Title: title,
			Product: apm.ProductReference{
				Id:       "share",
				Category: shared2.ServiceConfig.FileSystem.Name,
				Provider: cfg.Provider.Id,
			},
		},
		ProviderGeneratedId: util.OptValue(fmt.Sprintf("s-%s", share.Id)),
		CreatedBy:           util.OptValue("_ucloud"),
		Project:             util.OptNone[string](),
		ProjectAllRead:      false,
		ProjectAllWrite:     false,
	}))

	if herr != nil {
		return "", herr
	} else {
		return driveIdResp.Responses[0].Id, herr
	}
}

// NOTE(Brian) Function to support inherited sensitivity.
// This is legacy functionality, and thus only implemented for backwards compatibility.
func getInheritedSensitivity(drive *orc.Drive, internalPath string) util.Option[string] {
	ucloudPath, ok := InternalToUCloudWithDrive(drive, internalPath)
	validSensitivity := []string{"CONFIDENTIAL", "SENSITIVE", "PRIVATE"}

	if !ok {
		return util.OptNone[string]()
	}

	ancestors := util.Parents(ucloudPath)
	ancestors = append(ancestors, ucloudPath)

	result := ""

	for _, ancestor := range ancestors {
		// Skip checking drive
		if strings.Count(ancestor, "/") < 2 {
			continue
		}

		internalAncestorPath, ok, _ := UCloudToInternal(ancestor)

		if !ok {
			continue
		}

		fd, ok := OpenFile(internalAncestorPath, unix.O_RDONLY, 0)

		if !ok {
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
