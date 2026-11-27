package ptyhost

import (
	"bytes"
	"os"
	"os/exec"
	"sync"
	"syscall"
	"time"

	"github.com/creack/pty"
	"ucloud.dk/pkg/external/user"
)

const (
	PtyHostRingCapacity  = 512 * 1024
	PtyHostInitialCredit = int64(256 * 1024)
)

type PtyHostSink struct {
	Output     chan []byte
	Exited     chan int
	Closed     chan struct{}
	Once       sync.Once
	CreditMu   sync.Mutex
	CreditCond *sync.Cond
	Credit     int64
	IsClosed   bool
}

type PtyHostSession struct {
	Handle     string
	Ptmx       *os.File
	Cmd        *exec.Cmd
	Done       chan struct{}
	Mu         sync.Mutex
	Sink       *PtyHostSink
	Ring       []byte
	RingHead   int
	RingFull   bool
	TotalBytes uint64
	Exited     bool
	ExitCode   int
}

type PtyHostAttachResult struct {
	Sink          *PtyHostSink
	Snapshot      []byte
	BaseOffset    uint64
	ResetRequired bool
}

func PtyHostCloseSink(sink *PtyHostSink) {
	if sink == nil {
		return
	}
	sink.CreditMu.Lock()
	sink.IsClosed = true
	if sink.CreditCond != nil {
		sink.CreditCond.Broadcast()
	}
	sink.CreditMu.Unlock()

	sink.Once.Do(func() {
		close(sink.Closed)
	})
}

func PtyHostGrantCredit(sink *PtyHostSink, delta int64) {
	if sink == nil || delta <= 0 {
		return
	}
	sink.CreditMu.Lock()
	sink.Credit += delta
	if sink.CreditCond != nil {
		sink.CreditCond.Broadcast()
	}
	sink.CreditMu.Unlock()
}

func ptyHostConsumeCredit(sink *PtyHostSink, n int64) bool {
	if sink == nil {
		return false
	}
	sink.CreditMu.Lock()
	for sink.Credit <= 0 && !sink.IsClosed {
		sink.CreditCond.Wait()
	}
	if sink.IsClosed {
		sink.CreditMu.Unlock()
		return false
	}
	sink.Credit -= n
	sink.CreditMu.Unlock()
	return true
}

func PtyHostResolveShell() string {
	if sh := os.Getenv("SHELL"); sh != "" {
		_, err := os.Stat(sh)
		if err == nil {
			return sh
		}
	}
	u, userErr := user.Current()
	if userErr == nil && u.Shell != "" {
		_, statErr := os.Stat(u.Shell)
		if statErr == nil {
			return u.Shell
		}
	}
	_, bashErr := os.Stat("/bin/bash")
	if bashErr == nil {
		return "/bin/bash"
	}
	return "/bin/sh"
}

func PtyHostStartSession(
	handle string,
	cols int,
	rows int,
	workDir string,
	onExit func(handle string, exitCode int),
) (*PtyHostSession, error) {
	shellPath := PtyHostResolveShell()
	args := []string{}
	if shellPath != "/bin/sh" {
		args = append(args, "-l")
	}

	cmd := exec.Command(shellPath, args...)
	cmd.Env = append(os.Environ(), "TERM=xterm-256color", "COLORTERM=truecolor")
	if workDir != "" {
		info, statErr := os.Stat(workDir)
		if statErr == nil && info.IsDir() {
			cmd.Dir = workDir
		}
	}

	ptmx, err := pty.Start(cmd)
	if err != nil {
		return nil, err
	}

	session := &PtyHostSession{
		Handle: handle,
		Ptmx:   ptmx,
		Cmd:    cmd,
		Done:   make(chan struct{}),
		Ring:   make([]byte, PtyHostRingCapacity),
	}
	PtyHostResize(session, cols, rows)

	go ptyHostReadLoop(session)
	go ptyHostWaitLoop(session, onExit)

	return session, nil
}

func PtyHostResize(session *PtyHostSession, cols int, rows int) {
	if session == nil || session.Ptmx == nil || cols <= 0 || rows <= 0 {
		return
	}
	_ = pty.Setsize(session.Ptmx, &pty.Winsize{
		Rows: uint16(rows),
		Cols: uint16(cols),
	})
}

func PtyHostNudgeRedraw(session *PtyHostSession) {
	if session == nil || session.Ptmx == nil || session.Cmd == nil || session.Cmd.Process == nil {
		return
	}
	fgPgrp := PtyHostForegroundPgrp(session.Ptmx)
	shellPid := session.Cmd.Process.Pid
	if fgPgrp > 0 && fgPgrp != shellPid {
		_ = syscall.Kill(-fgPgrp, syscall.SIGWINCH)
	}
}

func PtyHostAttach(session *PtyHostSession, replay bool, lastOffset uint64) PtyHostAttachResult {
	sink := &PtyHostSink{
		Output: make(chan []byte, 128),
		Exited: make(chan int, 1),
		Closed: make(chan struct{}),
		Credit: PtyHostInitialCredit,
	}
	sink.CreditCond = sync.NewCond(&sink.CreditMu)

	session.Mu.Lock()
	oldSink := session.Sink
	session.Sink = sink
	alreadyExited := session.Exited
	exitCode := session.ExitCode
	totalBytes := session.TotalBytes

	var snapshot []byte
	baseOffset := totalBytes
	resetRequired := false

	if replay {
		ringLen := session.RingHead
		if session.RingFull {
			ringLen = len(session.Ring)
		}
		ringStart := totalBytes - uint64(ringLen)
		canDelta := lastOffset > 0 && lastOffset >= ringStart && lastOffset <= totalBytes
		if canDelta {
			deltaLen := int(totalBytes - lastOffset)
			if deltaLen > 0 {
				snapshot = ptyHostRingTailLocked(session, deltaLen)
			}
			baseOffset = lastOffset
			resetRequired = false
		} else {
			full := ptyHostRingSnapshotLocked(session)
			if session.RingFull && len(full) > 0 {
				idx := bytes.IndexByte(full, '\n')
				if idx >= 0 && idx+1 < len(full) && idx < 4096 {
					full = full[idx+1:]
				}
			}
			snapshot = full
			baseOffset = totalBytes - uint64(len(snapshot))
			resetRequired = true
		}
	}
	session.Mu.Unlock()

	if oldSink != nil {
		PtyHostCloseSink(oldSink)
	}
	if alreadyExited {
		select {
		case sink.Exited <- exitCode:
		default:
		}
	}

	return PtyHostAttachResult{
		Sink:          sink,
		Snapshot:      snapshot,
		BaseOffset:    baseOffset,
		ResetRequired: resetRequired,
	}
}

func PtyHostDetach(session *PtyHostSession, sink *PtyHostSink) {
	if session == nil || sink == nil {
		return
	}
	session.Mu.Lock()
	if session.Sink == sink {
		session.Sink = nil
	}
	session.Mu.Unlock()
	PtyHostCloseSink(sink)
}

func PtyHostWrite(session *PtyHostSession, data []byte) error {
	if session == nil || session.Ptmx == nil || len(data) == 0 {
		return nil
	}
	_, err := session.Ptmx.Write(data)
	return err
}

func PtyHostKill(session *PtyHostSession) {
	if session == nil {
		return
	}
	session.Mu.Lock()
	sink := session.Sink
	session.Sink = nil
	session.Exited = true
	session.Mu.Unlock()

	if sink != nil {
		PtyHostCloseSink(sink)
	}
	if session.Cmd != nil && session.Cmd.Process != nil {
		pid := session.Cmd.Process.Pid
		_ = syscall.Kill(-pid, syscall.SIGHUP)
	}
	if session.Ptmx != nil {
		_ = session.Ptmx.Close()
	}

	if session.Done != nil {
		select {
		case <-session.Done:
			return
		case <-time.After(350 * time.Millisecond):
		}
	}

	if session.Cmd != nil && session.Cmd.Process != nil {
		pid := session.Cmd.Process.Pid
		_ = syscall.Kill(-pid, syscall.SIGKILL)
		_ = session.Cmd.Process.Kill()
	}
}

func ptyHostRingAppendLocked(session *PtyHostSession, data []byte) {
	capLen := len(session.Ring)
	if capLen == 0 || len(data) == 0 {
		return
	}
	session.TotalBytes += uint64(len(data))
	if len(data) >= capLen {
		copy(session.Ring, data[len(data)-capLen:])
		session.RingHead = 0
		session.RingFull = true
		return
	}

	firstPart := capLen - session.RingHead
	if len(data) <= firstPart {
		copy(session.Ring[session.RingHead:], data)
		session.RingHead += len(data)
		if session.RingHead == capLen {
			session.RingHead = 0
			session.RingFull = true
		}
		return
	}

	copy(session.Ring[session.RingHead:], data[:firstPart])
	remaining := len(data) - firstPart
	copy(session.Ring[0:], data[firstPart:])
	session.RingHead = remaining
	session.RingFull = true
}

func ptyHostRingSnapshotLocked(session *PtyHostSession) []byte {
	if !session.RingFull {
		if session.RingHead == 0 {
			return nil
		}
		out := make([]byte, session.RingHead)
		copy(out, session.Ring[:session.RingHead])
		return out
	}

	capLen := len(session.Ring)
	out := make([]byte, capLen)
	tail := capLen - session.RingHead
	copy(out, session.Ring[session.RingHead:])
	copy(out[tail:], session.Ring[:session.RingHead])
	return out
}

func ptyHostRingTailLocked(session *PtyHostSession, length int) []byte {
	if length <= 0 {
		return nil
	}
	full := ptyHostRingSnapshotLocked(session)
	if length >= len(full) {
		return full
	}
	return full[len(full)-length:]
}

func ptyHostReadLoop(session *PtyHostSession) {
	buf := make([]byte, 32*1024)
	for {
		n, err := session.Ptmx.Read(buf)
		if n > 0 {
			chunk := make([]byte, n)
			copy(chunk, buf[:n])

			session.Mu.Lock()
			ptyHostRingAppendLocked(session, chunk)
			sink := session.Sink
			session.Mu.Unlock()

			if sink != nil {
				if ptyHostConsumeCredit(sink, int64(n)) {
					select {
					case sink.Output <- chunk:
					case <-sink.Closed:
					}
				}
			}
		}
		if err != nil {
			return
		}
	}
}

func ptyHostWaitLoop(session *PtyHostSession, onExit func(handle string, exitCode int)) {
	waitErr := session.Cmd.Wait()
	exitCode := 0
	if waitErr != nil {
		exitCode = 1
		if session.Cmd.ProcessState != nil {
			exitCode = session.Cmd.ProcessState.ExitCode()
		}
	}

	if session.Ptmx != nil {
		_ = session.Ptmx.Close()
	}

	session.Mu.Lock()
	session.Exited = true
	session.ExitCode = exitCode
	sink := session.Sink
	session.Mu.Unlock()

	close(session.Done)

	if sink != nil {
		select {
		case sink.Exited <- exitCode:
		default:
		}
	}

	if onExit != nil {
		onExit(session.Handle, exitCode)
	}
}
