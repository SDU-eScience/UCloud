package util

import (
	"os"
)

func fsRingPrepareFile(fd int, total int64, f *os.File) error {
	if err := f.Truncate(total); err != nil {
		_ = f.Close()
		return err
	}
	return nil
}
