//go:build darwin

package slurm

import (
    "os"
    "syscall"
    "time"
    fnd "ucloud.dk/pkg/foundation"
)

func FileAccessTime(info os.FileInfo) fnd.Timestamp {
    stat := info.Sys().(*syscall.Stat_t)
    sec, nsec := stat.Atimespec.Unix()
    return fnd.Timestamp(time.Unix(sec, nsec))
}

func FileModTime(info os.FileInfo) fnd.Timestamp {
    stat := info.Sys().(*syscall.Stat_t)
    sec, nsec := stat.Mtimespec.Unix()
    return fnd.Timestamp(time.Unix(sec, nsec))
}
