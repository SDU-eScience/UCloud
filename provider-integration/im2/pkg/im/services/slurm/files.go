package slurm

import (
    "fmt"
    lru "github.com/hashicorp/golang-lru/v2/expirable"
    "os"
    "slices"
    "strconv"
    "strings"
    "time"
    "ucloud.dk/pkg/foundation"
    "ucloud.dk/pkg/im/config"
    "ucloud.dk/pkg/orchestrators"
    "ucloud.dk/pkg/util"
)

var cfg *config.ServicesConfigurationSlurm
var browseCache *lru.LRU[string, []cachedDirEntry]

func InitializeFiles() {
    browseCache = lru.NewLRU[string, []cachedDirEntry](256, nil, 5*time.Minute)
}

// -- Start of the plugin interface stuff --
// NOTE(Dan): Let's assume that the plugin interface will look something like this (it will be in a different file)
type OpCode int

type ProviderMessage struct {
    Op   OpCode
    Data any
}

const (
    OpCodeFilesBrowse OpCode = iota
)

type SortDirection string

const (
    SortDirectionAscending  SortDirection = "ASCENDING"
    SortDirectionDescending SortDirection = "DESCENDING"
)

type FilesBrowseFlags struct {
    IncludePermissions bool
    IncludeTimestamps  bool
    IncludeSizes       bool
    IncludeUnixInfo    bool
    IncludeMetadata    bool

    FilterByFileExtension string
}

type ProviderMessageFilesBrowse struct {
    Path          string
    Drive         *orchestrators.Drive
    SortBy        string
    SortDirection SortDirection
    Next          string
    ItemsPerPage  int
    Flags         FilesBrowseFlags
}

func (op *ProviderMessage) FilesBrowse() *ProviderMessageFilesBrowse {
    if op.Op == OpCodeFilesBrowse {
        return op.Data.(*ProviderMessageFilesBrowse)
    }
    return nil
}

// -- End of the plugin interface stuff --

// NOTE(Dan): Let's assume that this is how the equivalent of a controller talks to us
func HandleFileMessage(message *ProviderMessage) bool {
    switch message.Op {
    case OpCodeFilesBrowse:
        browse(message.FilesBrowse())
    }
    return true
}

func mapPath(path string, drive *orchestrators.Drive) string {
    return path
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

func browse(request *ProviderMessageFilesBrowse) foundation.PageV2[orchestrators.ProviderFile] {
    internalPath := mapPath(request.Path, request.Drive)
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
            return foundation.EmptyPage[orchestrators.ProviderFile]()
        }

        fileNames, err := file.Readdirnames(-1)
        if err != nil {
            return foundation.EmptyPage[orchestrators.ProviderFile]()
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
        if request.SortDirection == SortDirectionDescending {
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
        return foundation.EmptyPage[orchestrators.ProviderFile]()
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

        item.Id = "TODO"
        //item.CreatedAt = ...
        //item.Status = ...
        //item.LegacySensitivity = ...
    }

    nextToken := ""
    if i < len(fileList) {
        nextToken = fmt.Sprintf("%v", i)
    }

    return foundation.PageV2[orchestrators.ProviderFile]{
        Items: items[:itemIdx],
        Next:  nextToken,
    }
}
