package ptyhost

import (
	"errors"
	"fmt"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"sync"
	"syscall"
	"time"
)

const ptyHostDaemonIdleTimeout = 60 * time.Second
const ptyHostMetaPollInterval = 500 * time.Millisecond

type PtyHostServer struct {
	Mu             sync.Mutex
	Sessions       map[string]*PtyHostSession
	DefaultWorkDir string
	ActiveConns    int
	IdleTimer      *time.Timer
	OnIdle         func()
}

type ptyHostChannelBinding struct {
	ChannelId uint16
	Session   *PtyHostSession
	Sink      *PtyHostSink
	StopMeta  chan struct{}
}

func PtyHostNewServer(defaultWorkDir string) *PtyHostServer {
	return &PtyHostServer{
		Sessions:       map[string]*PtyHostSession{},
		DefaultWorkDir: defaultWorkDir,
	}
}

func PtyHostSocketDir() string {
	return fmt.Sprintf("/tmp/.ucloud-pty-%d", os.Getuid())
}

func PtyHostDaemonSocketPath() string {
	return filepath.Join(PtyHostSocketDir(), "daemon.sock")
}

func PtyHostServerGetOrCreate(
	srv *PtyHostServer,
	handle string,
	cols int,
	rows int,
	sourceHandle string,
) (*PtyHostSession, bool, error) {
	if !PtyHostValidateHandle(handle) {
		return nil, false, errors.New("invalid handle")
	}

	srv.Mu.Lock()
	existing, ok := srv.Sessions[handle]
	if ok {
		existing.Mu.Lock()
		isExited := existing.Exited
		existing.Mu.Unlock()
		if !isExited {
			srv.Mu.Unlock()
			PtyHostResize(existing, cols, rows)
			return existing, false, nil
		}
	}

	workDir := srv.DefaultWorkDir
	if sourceHandle != "" {
		sourceSession, hasSource := srv.Sessions[sourceHandle]
		if hasSource {
			sourceSession.Mu.Lock()
			sourceExited := sourceSession.Exited
			sourceSession.Mu.Unlock()
			if !sourceExited {
				inheritedCwd := PtyHostForegroundCwd(sourceSession)
				if inheritedCwd != "" {
					workDir = inheritedCwd
				}
			}
		}
	}
	srv.Mu.Unlock()

	var created *PtyHostSession
	session, err := PtyHostStartSession(handle, cols, rows, workDir, func(exitedHandle string, _ int) {
		srv.Mu.Lock()
		if created != nil && srv.Sessions[exitedHandle] == created {
			delete(srv.Sessions, exitedHandle)
		}
		ptyHostServerScheduleIdleLocked(srv)
		srv.Mu.Unlock()
	})
	if err != nil {
		return nil, false, err
	}

	srv.Mu.Lock()
	created = session
	session.Mu.Lock()
	alreadyExited := session.Exited
	session.Mu.Unlock()
	if !alreadyExited {
		srv.Sessions[handle] = session
		if srv.IdleTimer != nil {
			srv.IdleTimer.Stop()
			srv.IdleTimer = nil
		}
	}
	srv.Mu.Unlock()

	return session, true, nil
}

func PtyHostServerKillHandle(srv *PtyHostServer, handle string) {
	if handle == "" {
		return
	}
	srv.Mu.Lock()
	session, ok := srv.Sessions[handle]
	if ok {
		delete(srv.Sessions, handle)
	}
	ptyHostServerScheduleIdleLocked(srv)
	srv.Mu.Unlock()

	if ok && session != nil {
		go PtyHostKill(session)
	}
}

func ptyHostServerScheduleIdleLocked(srv *PtyHostServer) {
	if srv.OnIdle == nil {
		return
	}
	if srv.ActiveConns > 0 || len(srv.Sessions) > 0 {
		if srv.IdleTimer != nil {
			srv.IdleTimer.Stop()
			srv.IdleTimer = nil
		}
		return
	}
	if srv.IdleTimer == nil {
		srv.IdleTimer = time.AfterFunc(ptyHostDaemonIdleTimeout, func() {
			srv.Mu.Lock()
			stillIdle := srv.ActiveConns == 0 && len(srv.Sessions) == 0
			srv.IdleTimer = nil
			onIdle := srv.OnIdle
			srv.Mu.Unlock()
			if stillIdle && onIdle != nil {
				onIdle()
			}
		})
	}
}

func PtyHostServeMuxStream(
	srv *PtyHostServer,
	readFrame func() (PtyHostMuxFrame, error),
	writeFrame func(op uint8, channelId uint16, payload []byte) error,
) {
	srv.Mu.Lock()
	srv.ActiveConns++
	if srv.IdleTimer != nil {
		srv.IdleTimer.Stop()
		srv.IdleTimer = nil
	}
	srv.Mu.Unlock()

	var bindingsMu sync.Mutex
	bindings := map[uint16]*ptyHostChannelBinding{}

	detachChannel := func(channelId uint16) {
		bindingsMu.Lock()
		b, ok := bindings[channelId]
		if ok {
			delete(bindings, channelId)
		}
		bindingsMu.Unlock()
		if ok {
			close(b.StopMeta)
			PtyHostDetach(b.Session, b.Sink)
		}
	}

	defer func() {
		bindingsMu.Lock()
		all := make([]*ptyHostChannelBinding, 0, len(bindings))
		for id, b := range bindings {
			all = append(all, b)
			delete(bindings, id)
		}
		bindingsMu.Unlock()

		for _, b := range all {
			close(b.StopMeta)
			PtyHostDetach(b.Session, b.Sink)
		}

		srv.Mu.Lock()
		srv.ActiveConns--
		ptyHostServerScheduleIdleLocked(srv)
		srv.Mu.Unlock()
	}()

	for {
		frame, err := readFrame()
		if err != nil {
			return
		}

		switch frame.Op {
		case PtyHostMuxOpAttach:
			req, decErr := PtyHostDecodeAttachPayload(frame.Payload)
			if decErr != nil {
				_ = writeFrame(PtyHostMuxOpError, frame.ChannelId, []byte(decErr.Error()))
				continue
			}

			detachChannel(frame.ChannelId)

			session, createdNew, startErr := PtyHostServerGetOrCreate(srv, req.Handle, req.Cols, req.Rows, req.SourceHandle)
			if startErr != nil {
				_ = writeFrame(PtyHostMuxOpError, frame.ChannelId, []byte(startErr.Error()))
				continue
			}

			attached := PtyHostAttach(session, req.Replay, req.LastOffset)
			binding := &ptyHostChannelBinding{
				ChannelId: frame.ChannelId,
				Session:   session,
				Sink:      attached.Sink,
				StopMeta:  make(chan struct{}),
			}

			bindingsMu.Lock()
			bindings[frame.ChannelId] = binding
			bindingsMu.Unlock()

			readyPayload := PtyHostEncodeReadyPayload(PtyHostReadyPayload{
				ResetRequired: attached.ResetRequired,
				BaseOffset:    attached.BaseOffset,
				ReplayBytes:   uint32(len(attached.Snapshot)),
			})
			readyErr := writeFrame(PtyHostMuxOpReady, frame.ChannelId, readyPayload)
			if readyErr != nil {
				return
			}

			if len(attached.Snapshot) > 0 {
				snapErr := writeFrame(PtyHostMuxOpOutput, frame.ChannelId, attached.Snapshot)
				if snapErr != nil {
					return
				}
			}

			if !createdNew && req.Replay {
				PtyHostNudgeRedraw(session)
			}

			go ptyHostPumpBinding(binding, writeFrame, func() {
				bindingsMu.Lock()
				if bindings[binding.ChannelId] == binding {
					delete(bindings, binding.ChannelId)
				}
				bindingsMu.Unlock()
			})
			go ptyHostWatchMetadata(binding, writeFrame)

		case PtyHostMuxOpInput:
			bindingsMu.Lock()
			b := bindings[frame.ChannelId]
			bindingsMu.Unlock()
			if b != nil {
				_ = PtyHostWrite(b.Session, frame.Payload)
			}

		case PtyHostMuxOpResize:
			cols, rows, decErr := PtyHostDecodeResizePayload(frame.Payload)
			if decErr != nil {
				continue
			}
			bindingsMu.Lock()
			b := bindings[frame.ChannelId]
			bindingsMu.Unlock()
			if b != nil {
				PtyHostResize(b.Session, cols, rows)
			}

		case PtyHostMuxOpCredit:
			delta, decErr := PtyHostDecodeCreditPayload(frame.Payload)
			if decErr != nil {
				continue
			}
			bindingsMu.Lock()
			b := bindings[frame.ChannelId]
			bindingsMu.Unlock()
			if b != nil {
				PtyHostGrantCredit(b.Sink, int64(delta))
			}

		case PtyHostMuxOpDetach:
			detachChannel(frame.ChannelId)

		case PtyHostMuxOpKill:
			handle := PtyHostDecodeKillPayload(frame.Payload)
			if handle == "" {
				bindingsMu.Lock()
				b := bindings[frame.ChannelId]
				bindingsMu.Unlock()
				if b != nil {
					handle = b.Session.Handle
				}
			}
			detachChannel(frame.ChannelId)
			if handle != "" {
				PtyHostServerKillHandle(srv, handle)
			}
		}
	}
}

func ptyHostPumpBinding(
	binding *ptyHostChannelBinding,
	writeFrame func(op uint8, channelId uint16, payload []byte) error,
	onRemoved func(),
) {
	defer onRemoved()
	for {
		select {
		case <-binding.Sink.Closed:
			return
		case exitCode := <-binding.Sink.Exited:
			_ = writeFrame(PtyHostMuxOpExited, binding.ChannelId, PtyHostEncodeExitedPayload(int32(exitCode)))
			return
		case chunk := <-binding.Sink.Output:
			err := writeFrame(PtyHostMuxOpOutput, binding.ChannelId, chunk)
			if err != nil {
				return
			}
		}
	}
}

func ptyHostWatchMetadata(
	binding *ptyHostChannelBinding,
	writeFrame func(op uint8, channelId uint16, payload []byte) error,
) {
	ticker := time.NewTicker(ptyHostMetaPollInterval)
	defer ticker.Stop()

	lastInfo := PtyHostProcessInfo{}
	initialized := false

	emitIfChanged := func() bool {
		info := PtyHostInspectProcess(binding.Session)
		if info.Command == "" && info.Cwd == "" {
			return true
		}
		if initialized && info == lastInfo {
			return true
		}
		commandDone := initialized && lastInfo.Busy && !info.Busy
		initialized = true
		lastInfo = info

		payload := PtyHostEncodeMetaPayload(PtyHostMetaPayload{
			Busy:        info.Busy,
			CommandDone: commandDone,
			Command:     info.Command,
			Cwd:         info.Cwd,
		})
		err := writeFrame(PtyHostMuxOpMeta, binding.ChannelId, payload)
		return err == nil
	}

	if !emitIfChanged() {
		return
	}

	for {
		select {
		case <-binding.StopMeta:
			return
		case <-binding.Sink.Closed:
			return
		case <-ticker.C:
			if !emitIfChanged() {
				return
			}
		}
	}
}

func PtyHostRunDaemon() error {
	dir := PtyHostSocketDir()
	err := os.MkdirAll(dir, 0700)
	if err != nil {
		return err
	}
	_ = os.Chmod(dir, 0700)

	sockPath := PtyHostDaemonSocketPath()
	existingConn, dialErr := net.DialTimeout("unix", sockPath, 300*time.Millisecond)
	if dialErr == nil {
		_ = existingConn.Close()
		return nil
	}
	_ = os.Remove(sockPath)

	listener, err := net.Listen("unix", sockPath)
	if err != nil {
		return err
	}
	_ = os.Chmod(sockPath, 0600)

	done := make(chan struct{})
	var doneOnce sync.Once
	stopDaemon := func() {
		doneOnce.Do(func() {
			close(done)
			_ = listener.Close()
			_ = os.Remove(sockPath)
		})
	}
	defer stopDaemon()

	srv := PtyHostNewServer("")
	srv.OnIdle = stopDaemon

	for {
		conn, acceptErr := listener.Accept()
		if acceptErr != nil {
			select {
			case <-done:
				return nil
			default:
				return acceptErr
			}
		}
		go ptyHostServeUnixConn(srv, conn)
	}
}

func ptyHostServeUnixConn(srv *PtyHostServer, conn net.Conn) {
	defer func() {
		_ = conn.Close()
	}()

	var writeMu sync.Mutex
	writeFrame := func(op uint8, channelId uint16, payload []byte) error {
		writeMu.Lock()
		defer writeMu.Unlock()
		return PtyHostWriteMuxFrame(conn, op, channelId, payload)
	}
	readFrame := func() (PtyHostMuxFrame, error) {
		return PtyHostReadMuxFrame(conn)
	}

	PtyHostServeMuxStream(srv, readFrame, writeFrame)
}

func PtyHostDialOrSpawnDaemon() (net.Conn, error) {
	sockPath := PtyHostDaemonSocketPath()
	conn, err := net.DialTimeout("unix", sockPath, 1*time.Second)
	if err == nil {
		return conn, nil
	}

	_ = os.Remove(sockPath)

	exePath, exeErr := os.Executable()
	if exeErr != nil {
		return nil, exeErr
	}

	daemonCmd := exec.Command(exePath, "daemon")
	daemonCmd.Stdin = nil
	daemonCmd.Stdout = nil
	daemonCmd.Stderr = nil
	daemonCmd.SysProcAttr = &syscall.SysProcAttr{
		Setsid: true,
	}
	startErr := daemonCmd.Start()
	if startErr != nil {
		return nil, startErr
	}
	_ = daemonCmd.Process.Release()

	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		conn, err = net.DialTimeout("unix", sockPath, 300*time.Millisecond)
		if err == nil {
			return conn, nil
		}
		time.Sleep(25 * time.Millisecond)
	}
	return nil, errors.New("timed out waiting for ucloud-pty daemon")
}

func PtyHostRunMuxStdio() error {
	conn, err := PtyHostDialOrSpawnDaemon()
	if err != nil {
		return err
	}
	defer func() {
		_ = conn.Close()
	}()

	var stdoutMu sync.Mutex
	writeStdout := func(frame PtyHostMuxFrame) error {
		stdoutMu.Lock()
		defer stdoutMu.Unlock()
		return PtyHostWriteMuxFrame(os.Stdout, frame.Op, frame.ChannelId, frame.Payload)
	}

	stdinDone := make(chan struct{})
	go func() {
		defer close(stdinDone)
		for {
			frame, readErr := PtyHostReadMuxFrame(os.Stdin)
			if readErr != nil {
				_ = conn.Close()
				return
			}
			writeErr := PtyHostWriteMuxFrame(conn, frame.Op, frame.ChannelId, frame.Payload)
			if writeErr != nil {
				return
			}
		}
	}()

	for {
		frame, readErr := PtyHostReadMuxFrame(conn)
		if readErr != nil {
			return nil
		}
		writeErr := writeStdout(frame)
		if writeErr != nil {
			return writeErr
		}
	}
}

func PtyHostKillHandles(handles []string) {
	sockPath := PtyHostDaemonSocketPath()
	conn, err := net.DialTimeout("unix", sockPath, 500*time.Millisecond)
	if err != nil {
		return
	}
	defer func() {
		_ = conn.Close()
	}()

	for _, handle := range handles {
		if !PtyHostValidateHandle(handle) {
			continue
		}
		_ = PtyHostWriteMuxFrame(conn, PtyHostMuxOpKill, 0, PtyHostEncodeKillPayload(handle))
	}
}
