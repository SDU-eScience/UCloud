//go:build !windows && !plan9

package filesystem

import (
	"golang.org/x/sys/unix"
	"os"
	"syscall"
)

func FileUid(info os.FileInfo) int {
	stat := info.Sys().(*syscall.Stat_t)
	return int(stat.Uid)
}

func FileGid(info os.FileInfo) int {
	stat := info.Sys().(*syscall.Stat_t)
	return int(stat.Gid)
}

func FileOpenAt(dir *os.File, child string, mode int, perm uint32) (*os.File, error) {
	fd, err := unix.Openat(int(dir.Fd()), child, mode, perm)
	if err != nil {
		return nil, err
	}

	return os.NewFile(uintptr(fd), child), nil
}
