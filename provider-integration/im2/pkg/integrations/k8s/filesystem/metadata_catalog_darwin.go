package filesystem

import (
	"os"
	"time"

	"golang.org/x/sys/unix"
)

func metadataLookupEntry(parent int, name string, generation uint64) (MetadataEntry, error) {
	var stat unix.Stat_t
	startedAt := time.Now()
	err := unix.Fstatat(parent, name, &stat, unix.AT_SYMLINK_NOFOLLOW)
	metadataRecordFsOperation("fstatat", startedAt, err)
	if err != nil {
		return MetadataEntry{}, err
	}
	return metadataEntryFromStat(&stat, generation), nil
}

func metadataReadScanChildren(directory *os.File, buffer []byte) ([]metadataScanChild, error) {
	startedAt := time.Now()
	names, err := directory.Readdirnames(1024)
	metadataRecordFsOperation("readdir", startedAt, err)
	children := make([]metadataScanChild, len(names))
	for i, name := range names {
		children[i].name = name
	}
	return children, err
}
