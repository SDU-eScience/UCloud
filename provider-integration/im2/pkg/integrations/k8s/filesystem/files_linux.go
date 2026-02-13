//go:build linux

package filesystem

import (
	"os"
	"syscall"
	"time"
	fnd "ucloud.dk/shared/pkg/foundation"
)

func FileAccessTime(info os.FileInfo) fnd.Timestamp {
	stat := info.Sys().(*syscall.Stat_t)
	sec, nsec := stat.Atim.Unix()
	return fnd.Timestamp(time.Unix(sec, nsec))
}

func FileModTime(info os.FileInfo) fnd.Timestamp {
	stat := info.Sys().(*syscall.Stat_t)
	sec, nsec := stat.Mtim.Unix()
	return fnd.Timestamp(time.Unix(sec, nsec))
}
