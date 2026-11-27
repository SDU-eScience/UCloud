package ptyhost

import (
	"fmt"
	"os"
	"strings"
	"syscall"
	"unsafe"
)

type PtyHostProcessInfo struct {
	Command string
	Cwd     string
	Busy    bool
}

func PtyHostForegroundPgrp(ptmx *os.File) int {
	if ptmx == nil {
		return 0
	}
	var pgrp int32
	_, _, errno := syscall.Syscall(
		syscall.SYS_IOCTL,
		ptmx.Fd(),
		uintptr(syscall.TIOCGPGRP),
		uintptr(unsafe.Pointer(&pgrp)),
	)
	if errno != 0 {
		return 0
	}
	return int(pgrp)
}

func PtyHostInspectProcess(session *PtyHostSession) PtyHostProcessInfo {
	if session == nil || session.Cmd == nil || session.Cmd.Process == nil {
		return PtyHostProcessInfo{}
	}

	shellPid := session.Cmd.Process.Pid
	fgPid := PtyHostForegroundPgrp(session.Ptmx)
	if fgPid <= 0 {
		fgPid = shellPid
	}

	busy := fgPid != shellPid
	command := ptyHostReadProcComm(fgPid)
	cwd := ptyHostReadProcCwd(fgPid)
	if cwd == "" && fgPid != shellPid {
		cwd = ptyHostReadProcCwd(shellPid)
	}

	return PtyHostProcessInfo{
		Command: command,
		Cwd:     cwd,
		Busy:    busy,
	}
}

func PtyHostForegroundCwd(session *PtyHostSession) string {
	return PtyHostInspectProcess(session).Cwd
}

func ptyHostReadProcComm(pid int) string {
	raw, err := os.ReadFile(fmt.Sprintf("/proc/%d/comm", pid))
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(raw))
}

func ptyHostReadProcCwd(pid int) string {
	link, err := os.Readlink(fmt.Sprintf("/proc/%d/cwd", pid))
	if err != nil {
		return ""
	}
	return link
}
