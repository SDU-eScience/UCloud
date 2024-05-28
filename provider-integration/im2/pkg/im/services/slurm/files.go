package slurm

import (
	"fmt"
	lru "github.com/hashicorp/golang-lru/v2/expirable"
	"net/http"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"time"
	fnd "ucloud.dk/pkg/foundation"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var browseCache *lru.LRU[string, []cachedDirEntry]

func InitializeFiles() ctrl.FileService {
	browseCache = lru.NewLRU[string, []cachedDirEntry](256, nil, 5*time.Minute)
	return ctrl.FileService{
		BrowseFiles:  browse,
		CreateFolder: nil,
	}
}

// NOTE(Dan): Let's assume that this is how the equivalent of a controller talks to us

func mapPath(path string, drive *orchestrators.Drive) string {
	elements := strings.Split(filepath.Clean(path), "/")
	if len(elements) < 3 {
		return "/home/user"
	}
	return "/home/user/" + strings.Join(elements[2:], "/")
}

func inverseMap(path string, drive *orchestrators.Drive) string {
	list := strings.Split(filepath.Clean(path), "/")
	if len(list) < 3 {
		return ""
	}
	return "/" + drive.Id + "/" + strings.Join(list[3:], "/")
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

func browse(request ctrl.BrowseFilesRequest) (fnd.PageV2[orchestrators.ProviderFile], error) {
	internalPath := mapPath(request.Path, &request.Drive)
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
			return fnd.EmptyPage[orchestrators.ProviderFile](), &util.HttpError{
				StatusCode: http.StatusNotFound,
				Why:        "Could not find directory",
			}
		}

		fileNames, err := file.Readdirnames(-1)
		if err != nil {
			return fnd.EmptyPage[orchestrators.ProviderFile](), &util.HttpError{
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
		if request.SortDirection == orchestrators.SortDirectionDescending {
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
		return fnd.EmptyPage[orchestrators.ProviderFile](), nil
	}

	items := make([]orchestrators.ProviderFile, min(request.ItemsPerPage, len(fileList)-offset))

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

		readMetadata(entry.absPath, entry.info, item, &request.Drive)
	}

	nextToken := ""
	if i < len(fileList) {
		nextToken = fmt.Sprintf("%v", i)
	}

	return fnd.PageV2[orchestrators.ProviderFile]{
		Items:        items[:itemIdx],
		ItemsPerPage: 250,
		Next:         util.OptValue(nextToken),
	}, nil
}

func readMetadata(internalPath string, stat os.FileInfo, file *orchestrators.ProviderFile, drive *orchestrators.Drive) {
	file.Status.Type = orchestrators.FileTypeFile
	if stat.IsDir() {
		file.Status.Type = orchestrators.FileTypeDirectory
	}

	// TODO These two
	file.Id = inverseMap(internalPath, drive)
	file.Status.Icon = orchestrators.FileIconHintNone

	file.CreatedAt = FileModTime(stat)
	file.Status.ModifiedAt = FileModTime(stat)
	file.Status.AccessedAt = FileAccessTime(stat)

	file.Status.SizeInBytes = stat.Size()
	file.Status.SizeIncludingChildrenInBytes = stat.Size()

	file.Status.UnixOwner = FileUid(stat)
	file.Status.UnixGroup = FileGid(stat)
	file.Status.UnixMode = int(stat.Mode()) & 0777 // only keep permissions bits
}
