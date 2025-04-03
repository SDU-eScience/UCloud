package slurm

import (
	"golang.org/x/sys/unix"
	"os"
	"syscall"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

func CreateAndForkPty(cmd []string, envp []string, workingDir util.Option[string]) (*os.File, int, error) {
	log.Info("pty 1")
	mfd, sfd, err := openPty()
	if err != nil {
		log.Info("pty 2: %s", err)
		return nil, 0, err
	}

	log.Info("pty 3")
	wdir := workingDir.Value
	if !workingDir.Present {
		log.Info("pty 4")
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
	log.Info("pty 5")

	pid, err := syscall.ForkExec(cmd[0], cmd, attr)
	log.Info("pty 6")
	if err != nil {
		log.Info("pty 7")
		panic("fork failed")
	}

	log.Info("pty 8")
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
	log.Info("openpty 1")
	mfd, err := syscall.Open("/dev/ptmx", syscall.O_RDWR|syscall.O_NOCTTY, 0)
	if err != nil {
		log.Info("openpty 2")
		return -1, -1, err
	}
	log.Info("openpty 3")

	// Unlock PTY
	err = unix.IoctlSetPointerInt(mfd, TIOCSPTLCK, 0)
	log.Info("openpty 4")
	if err != nil {
		log.Info("openpty 5")
		_ = syscall.Close(mfd)
		return -1, -1, err
	}

	// Get slave PTY name
	log.Info("openpty 6")
	ptsName, err := unix.IoctlGetInt(mfd, TIOCGPTN)
	if err != nil {
		log.Info("openpty 7")
		_ = syscall.Close(mfd)
		return -1, -1, err
	}
	log.Info("openpty 8")

	slaveName := "/dev/pts/" + string(rune(ptsName+'0'))
	sfd, err := syscall.Open(slaveName, syscall.O_RDWR|syscall.O_NOCTTY, 0)
	if err != nil {
		log.Info("openpty 9")
		_ = syscall.Close(mfd)
		return -1, -1, err
	}

	log.Info("openpty 10")
	return mfd, sfd, nil
}
