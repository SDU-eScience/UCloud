package slurm

import (
	"encoding/json"
	"fmt"
	lru "github.com/hashicorp/golang-lru/v2/expirable"
	"io"
	"net/http"
	"os"
	"slices"
	"strconv"
	"strings"
	"time"
	fnd "ucloud.dk/pkg/foundation"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var browseCache *lru.LRU[string, []cachedDirEntry]

func InitializeFiles() ctrl.FileService {
	browseCache = lru.NewLRU[string, []cachedDirEntry](256, nil, 5*time.Minute)
	return ctrl.FileService{
		BrowseFiles:           browse,
		RetrieveFile:          retrieve,
		CreateFolder:          createFolder,
		Move:                  move,
		CreateDownloadSession: createDownload,
		Download:              download,
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

func move(request ctrl.MoveFileRequest) error {
	sourcePath := UCloudToInternalWithDrive(request.OldDrive, request.OldPath)
	destPath := UCloudToInternalWithDrive(request.NewDrive, request.NewPath)

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
