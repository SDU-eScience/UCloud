package filesystem

import (
	"bytes"
	"encoding/binary"
	"errors"
	"io"
	"os"
	"sync/atomic"
	"time"
	"unsafe"

	"golang.org/x/sys/unix"
	"ucloud.dk/shared/pkg/util"
)

var metadataStatxUnavailable atomic.Bool

const metadataStatxMask = unix.STATX_TYPE | unix.STATX_MODE | unix.STATX_NLINK | unix.STATX_ATIME | unix.STATX_MTIME | unix.STATX_INO | unix.STATX_SIZE | unix.STATX_BLOCKS

func metadataLookupEntry(parent int, name string, generation uint64) (MetadataEntry, error) {
	fallbackReason := "unavailable"
	if !metadataStatxUnavailable.Load() {
		var stat unix.Statx_t
		startedAt := time.Now()
		err := unix.Statx(parent, name, unix.AT_SYMLINK_NOFOLLOW|unix.AT_STATX_SYNC_AS_STAT, metadataStatxMask, &stat)
		metadataRecordFsOperation("statx", startedAt, err)
		if err == nil && stat.Mask&metadataStatxMask == metadataStatxMask {
			entry := metadataEntryFromMode(uint32(stat.Mode), generation)
			entry.LogicalSize = stat.Size
			entry.AllocatedSize = util.OptValue(stat.Blocks * 512)
			entry.ModificationTime = stat.Mtime.Sec*int64(time.Second) + int64(stat.Mtime.Nsec)
			entry.AccessTime = util.OptValue(stat.Atime.Sec*int64(time.Second) + int64(stat.Atime.Nsec))
			entry.DeviceID = util.OptValue(unix.Mkdev(stat.Dev_major, stat.Dev_minor))
			entry.InodeID = util.OptValue(stat.Ino)
			entry.LinkCount = util.OptValue(uint64(stat.Nlink))
			return entry, nil
		}
		unsupported := errors.Is(err, unix.ENOSYS) || errors.Is(err, unix.EOPNOTSUPP) || errors.Is(err, unix.EINVAL)
		if err != nil && !unsupported {
			return MetadataEntry{}, err
		}
		if errors.Is(err, unix.ENOSYS) {
			metadataStatxUnavailable.Store(true)
			fallbackReason = "unsupported_syscall"
		} else if errors.Is(err, unix.EOPNOTSUPP) {
			fallbackReason = "unsupported_filesystem"
		} else if errors.Is(err, unix.EINVAL) {
			fallbackReason = "unsupported_flags"
		} else {
			fallbackReason = "missing_attributes"
		}
	}
	metadataMetricStatxFallbacks.WithLabelValues(fallbackReason).Inc()
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
	var count int
	var err error
	for {
		startedAt := time.Now()
		count, err = unix.ReadDirent(int(directory.Fd()), buffer)
		metadataRecordFsOperation("readdir", startedAt, err)
		if !errors.Is(err, unix.EINTR) {
			break
		}
	}
	if err != nil {
		return nil, err
	}
	if count == 0 {
		return nil, io.EOF
	}
	const nameOffset = int(unsafe.Offsetof(unix.Dirent{}.Name))
	const inodeOffset = int(unsafe.Offsetof(unix.Dirent{}.Ino))
	const lengthOffset = int(unsafe.Offsetof(unix.Dirent{}.Reclen))
	const typeOffset = int(unsafe.Offsetof(unix.Dirent{}.Type))
	var children []metadataScanChild
	for data := buffer[:count]; len(data) > 0; {
		if len(data) < nameOffset+1 {
			return nil, io.ErrUnexpectedEOF
		}
		length := int(binary.NativeEndian.Uint16(data[lengthOffset : lengthOffset+2]))
		if length < nameOffset+1 || length > len(data) {
			return nil, io.ErrUnexpectedEOF
		}
		name := data[nameOffset:length]
		end := bytes.IndexByte(name, 0)
		if end < 0 {
			return nil, io.ErrUnexpectedEOF
		}
		name = name[:end]
		inode := binary.NativeEndian.Uint64(data[inodeOffset : inodeOffset+8])
		isChild := inode != 0 && len(name) > 0 && !bytes.Equal(name, []byte(".")) && !bytes.Equal(name, []byte(".."))
		if isChild {
			children = append(children, metadataScanChild{
				name:      string(name),
				directory: data[typeOffset] == unix.DT_DIR,
			})
		}
		data = data[length:]
	}
	return children, nil
}
