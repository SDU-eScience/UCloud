package slurm

import (
	"fmt"
	"golang.org/x/sys/unix"
	"os"
	"syscall"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

func CreateAndForkPty(cmd []string, envp []string, workingDir util.Option[string]) (*os.File, int, error) {
	mfd, sfd, err := openPty()
	if err != nil {
		log.Info("CreateAndForkPty failed: %s", err)
		return nil, 0, err
	}

	wdir := workingDir.Value
	if !workingDir.Present {
		wdir, _ = os.Getwd()
	}

	attr := &syscall.ProcAttr{
		Dir: wdir,
		Env: envp,
		Files: []uintptr{
			uintptr(sfd), // stdin
			uintptr(sfd), // stdout
			uintptr(sfd), // stderr
		},
		Sys: &syscall.SysProcAttr{
			Setsid:  true,
			Setctty: true,
		},
	}

	pid, err := syscall.ForkExec(cmd[0], cmd, attr)
	if err != nil {
		panic("fork failed")
	}

	_ = syscall.Close(sfd)
	return os.NewFile(uintptr(mfd), "pty"), pid, nil
}

func ResizePty(masterFd *os.File, cols int, rows int) {
	winsize := &unix.Winsize{
		Col: uint16(cols),
		Row: uint16(rows),
	}
	_ = unix.IoctlSetWinsize(int(masterFd.Fd()), syscall.TIOCSWINSZ, winsize)
}

func openPty() (int, int, error) {
	mfd, err := syscall.Open("/dev/ptmx", syscall.O_RDWR|syscall.O_NOCTTY, 0)
	if err != nil {
		log.Info("openPty failed. Could not open /dev/ptmx: %s", err)
		return -1, -1, err
	}

	// Unlock PTY
	err = unix.IoctlSetPointerInt(mfd, TIOCSPTLCK, 0)
	if err != nil {
		log.Info("openPty failed (TIOCSPTLCK): %s", err)
		_ = syscall.Close(mfd)
		return -1, -1, err
	}

	// Get slave PTY name
	ptsName, err := unix.IoctlGetInt(mfd, TIOCGPTN)
	if err != nil {
		log.Info("openPty failed (TIOCGPTN): %s", err)
		_ = syscall.Close(mfd)
		return -1, -1, err
	}

	slaveName := fmt.Sprintf("/dev/pts/%d", ptsName)
	sfd, err := syscall.Open(slaveName, syscall.O_RDWR|syscall.O_NOCTTY, 0)
	if err != nil {
		log.Info("openPty failed %s: %s", slaveName, err)
		_ = syscall.Close(mfd)
		return -1, -1, err
	}

	return mfd, sfd, nil
}
