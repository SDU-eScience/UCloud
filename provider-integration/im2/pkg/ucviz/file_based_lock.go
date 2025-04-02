package ucviz

import (
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"ucloud.dk/pkg/util"
)

type fileLock struct {
	path   string
	handle *os.File
}

func acquireLock(filePath string) (fileLock, error) {
	file, err := os.OpenFile(filePath, os.O_CREATE|os.O_RDWR|os.O_CREATE, 0644)
	if err != nil {
		return fileLock{}, err
	}

	if err := syscall.Flock(int(file.Fd()), syscall.LOCK_EX); err != nil {
		util.SilentClose(file)
		return fileLock{}, fmt.Errorf("failed to acquire lock: %v", err)
	}

	result := fileLock{
		path:   filePath,
		handle: file,
	}

	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt, syscall.SIGTERM, syscall.SIGKILL)
	go func() {
		<-c
		result.Release()
		os.Exit(1)
	}()

	return result, nil
}

func (l *fileLock) Release() {
	_ = os.Remove(l.path)
	_ = syscall.Flock(int(l.handle.Fd()), syscall.LOCK_UN)
	util.SilentClose(l.handle)
}
