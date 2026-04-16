package filesystem

import (
	"io/fs"
	"path/filepath"
	"time"

	"golang.org/x/sys/unix"
	"ucloud.dk/shared/pkg/util"
)

func StatInternal(internalPath string) (fs.FileInfo, *util.HttpError) {
	file, ok := OpenFile(internalPath, unix.O_RDONLY, 0)
	if !ok {
		return nil, util.UserHttpError("Could not find file")
	}
	defer util.SilentClose(file)

	info, err := file.Stat()
	if err != nil {
		return nil, util.UserHttpError("Could not find file")
	}

	return info, nil
}

func ReadDirInternal(internalPath string) ([]fs.FileInfo, *util.HttpError) {
	file, ok := OpenFile(internalPath, unix.O_RDONLY, 0)
	if !ok {
		return nil, util.UserHttpError("Could not find directory")
	}
	defer util.SilentClose(file)

	infos, err := file.Readdir(-1)
	if err != nil {
		return nil, util.UserHttpError("Could not find directory")
	}

	result := make([]fs.FileInfo, len(infos))
	for i, info := range infos {
		result[i] = info
	}
	return result, nil
}

func RemoveInternal(internalPath string) *util.HttpError {
	info, herr := StatInternal(internalPath)
	if herr != nil {
		return herr
	}

	parentDir, ok := OpenFile(filepath.Dir(internalPath), unix.O_RDONLY, 0)
	if !ok {
		return util.UserHttpError("Could not find file")
	}
	defer util.SilentClose(parentDir)

	flag := 0
	if info.IsDir() {
		flag = unix.AT_REMOVEDIR
	}

	if err := unix.Unlinkat(int(parentDir.Fd()), filepath.Base(internalPath), flag); err != nil {
		return util.UserHttpError("Could not remove file")
	}

	return nil
}

func RenameInternal(oldInternalPath, newInternalPath string) *util.HttpError {
	sourceParent, ok1 := OpenFile(filepath.Dir(oldInternalPath), unix.O_RDONLY, 0)
	destParent, ok2 := OpenFile(filepath.Dir(newInternalPath), unix.O_RDONLY, 0)
	defer util.SilentClose(sourceParent)
	defer util.SilentClose(destParent)
	if !ok1 || !ok2 {
		return util.UserHttpError("Could not rename file")
	}

	if err := unix.Renameat(
		int(sourceParent.Fd()),
		filepath.Base(oldInternalPath),
		int(destParent.Fd()),
		filepath.Base(newInternalPath),
	); err != nil {
		return util.UserHttpError("Could not rename file")
	}

	return nil
}

func ChmodInternal(internalPath string, mode fs.FileMode) *util.HttpError {
	file, ok := OpenFile(internalPath, unix.O_RDONLY, 0)
	if !ok {
		return util.UserHttpError("Could not find file")
	}
	defer util.SilentClose(file)

	if err := unix.Fchmod(int(file.Fd()), uint32(mode.Perm())); err != nil {
		return util.UserHttpError("Could not update file permissions")
	}

	return nil
}

func ChtimesInternal(internalPath string, atime, mtime time.Time) *util.HttpError {
	file, ok := OpenFile(internalPath, unix.O_RDONLY, 0)
	if !ok {
		return util.UserHttpError("Could not find file")
	}
	defer util.SilentClose(file)

	utimes := []unix.Timeval{
		unix.NsecToTimeval(atime.UnixNano()),
		unix.NsecToTimeval(mtime.UnixNano()),
	}
	if err := unix.Futimes(int(file.Fd()), utimes); err != nil {
		return util.UserHttpError("Could not update file timestamps")
	}

	return nil
}
