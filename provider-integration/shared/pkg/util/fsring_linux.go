package util

import (
	"golang.org/x/sys/unix"
	"os"
)

func fsRingPrepareFile(fd int, total int64, f *os.File) error {
	// Prefer fallocate, otherwise use truncate.
	if err := unix.Fallocate(fd, 0, 0, total); err != nil {
		if err := f.Truncate(total); err != nil {
			_ = f.Close()
			return err
		}
	}
	return nil
}
