//go:build !windows && !plan9

package slurm

import (
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
