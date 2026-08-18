package filesystem

import (
	"fmt"
	"io"
	"os"
	"path/filepath"

	"golang.org/x/sys/unix"
	"ucloud.dk/shared/pkg/util"
)

func ReadFile(path string, maxBytes int64) ([]byte, *util.HttpError) {
	file, ok := OpenFile(path, unix.O_RDONLY, 0)
	if !ok {
		return nil, util.UserHttpError("Could not find file")
	}
	defer util.SilentClose(file)

	info, err := file.Stat()
	if err != nil || info.IsDir() {
		return nil, util.UserHttpError("Could not read file")
	}
	if maxBytes > 0 && info.Size() > maxBytes {
		return nil, util.UserHttpError("File is too large")
	}

	data, err := io.ReadAll(file)
	if err != nil {
		return nil, util.UserHttpError("Could not read file")
	}
	return data, nil
}

func ListDirNames(path string) ([]string, *util.HttpError) {
	dir, ok := OpenFile(path, unix.O_RDONLY, 0)
	if !ok {
		return nil, util.UserHttpError("Could not find directory")
	}
	defer util.SilentClose(dir)

	info, err := dir.Stat()
	if err != nil || !info.IsDir() {
		return nil, util.UserHttpError("Could not read directory")
	}

	names, err := dir.Readdirnames(-1)
	if err != nil {
		return nil, util.UserHttpError("Could not read directory")
	}
	return names, nil
}

func Stat(path string) (os.FileInfo, *util.HttpError) {
	file, ok := OpenFile(path, unix.O_RDONLY, 0)
	if !ok {
		return nil, util.UserHttpError("Could not find file")
	}
	defer util.SilentClose(file)

	info, err := file.Stat()
	if err != nil {
		return nil, util.UserHttpError("Could not stat file")
	}
	return info, nil
}

func WriteFileAtomic(path string, data []byte, perm uint32) *util.HttpError {
	if err := DoCreateFolder(filepath.Dir(path)); err != nil {
		return err
	}

	tmpPath := filepath.Join(filepath.Dir(path), fmt.Sprintf(".%s.tmp-%s", filepath.Base(path), util.RandomToken(16)))
	tmpFile, ok := OpenFile(tmpPath, unix.O_WRONLY|unix.O_CREAT|unix.O_EXCL, perm)
	if !ok {
		return util.UserHttpError("Could not create temporary file")
	}

	writeErr := error(nil)
	if written, err := tmpFile.Write(data); err != nil {
		writeErr = err
	} else if written != len(data) {
		writeErr = io.ErrShortWrite
	}
	if writeErr == nil {
		writeErr = tmpFile.Sync()
	}
	closeErr := tmpFile.Close()
	if writeErr != nil || closeErr != nil {
		deleteTemporaryFile(tmpPath)
		return util.UserHttpError("Could not write file")
	}

	parent, ok := OpenFile(filepath.Dir(path), unix.O_RDONLY, 0)
	if !ok {
		deleteTemporaryFile(tmpPath)
		return util.UserHttpError("Could not write file")
	}
	defer util.SilentClose(parent)

	if err := unix.Renameat(int(parent.Fd()), filepath.Base(tmpPath), int(parent.Fd()), filepath.Base(path)); err != nil {
		deleteTemporaryFile(tmpPath)
		return util.UserHttpError("Could not write file")
	}
	return nil
}

func deleteTemporaryFile(path string) {
	parent, ok := OpenFile(filepath.Dir(path), unix.O_RDONLY, 0)
	if !ok {
		return
	}
	defer util.SilentClose(parent)
	_ = unix.Unlinkat(int(parent.Fd()), filepath.Base(path), 0)
}
