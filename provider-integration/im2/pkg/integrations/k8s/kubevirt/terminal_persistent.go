package kubevirt

import (
	"context"
	"io"
	"sync"
	"time"

	ws "github.com/gorilla/websocket"
	kvapi "kubevirt.io/client-go/kubevirt/typed/core/v1"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/ptyhost"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	tp "ucloud.dk/shared/pkg/terminalproto"
	"ucloud.dk/shared/pkg/util"
)

const kubevirtMuxIdleClose = 30 * time.Second

type kubevirtMuxChannel struct {
	Id     uint16
	Req    *ctrl.PersistentPaneRequest
	Closed chan struct{}
	Once   sync.Once
}

type kubevirtVmMux struct {
	Key           string
	JobId         string
	Conn          *ws.Conn
	WriteMu       sync.Mutex
	Mu            sync.Mutex
	Channels      map[uint16]*kubevirtMuxChannel
	NextChannelId uint16
	Closed        bool
	IdleTimer     *time.Timer
}

var kubevirtVmMuxMu sync.Mutex
var kubevirtVmMuxes = map[string]*kubevirtVmMux{}

func kubevirtCloseMuxChannel(ch *kubevirtMuxChannel) {
	ch.Once.Do(func() {
		close(ch.Closed)
	})
}

func kubevirtGetOrCreateVmMux(ctx context.Context, jobId string) *kubevirtVmMux {
	kubevirtVmMuxMu.Lock()
	existing, ok := kubevirtVmMuxes[jobId]
	if ok {
		existing.Mu.Lock()
		isOpen := !existing.Closed
		if isOpen && existing.IdleTimer != nil {
			existing.IdleTimer.Stop()
			existing.IdleTimer = nil
		}
		existing.Mu.Unlock()
		if isOpen {
			kubevirtVmMuxMu.Unlock()
			return existing
		}
	}
	kubevirtVmMuxMu.Unlock()

	ttyCtx, ttyCancel := context.WithTimeout(ctx, 5*time.Second)
	conn := vmaRequestTty(ttyCtx, jobId)
	ttyCancel()
	if conn == nil {
		return nil
	}

	mux := &kubevirtVmMux{
		Key:           jobId,
		JobId:         jobId,
		Conn:          conn,
		Channels:      map[uint16]*kubevirtMuxChannel{},
		NextChannelId: 1,
	}

	kubevirtVmMuxMu.Lock()
	if prev, hasPrev := kubevirtVmMuxes[jobId]; hasPrev {
		prev.Mu.Lock()
		prevOpen := !prev.Closed
		prev.Mu.Unlock()
		if prevOpen {
			kubevirtVmMuxMu.Unlock()
			util.SilentClose(conn)
			return prev
		}
	}
	kubevirtVmMuxes[jobId] = mux
	kubevirtVmMuxMu.Unlock()

	go kubevirtVmMuxReadLoop(mux)
	return mux
}

func kubevirtCloseVmMux(mux *kubevirtVmMux) {
	mux.Mu.Lock()
	if mux.Closed {
		mux.Mu.Unlock()
		return
	}
	mux.Closed = true
	if mux.IdleTimer != nil {
		mux.IdleTimer.Stop()
		mux.IdleTimer = nil
	}
	channels := make([]*kubevirtMuxChannel, 0, len(mux.Channels))
	for id, ch := range mux.Channels {
		channels = append(channels, ch)
		delete(mux.Channels, id)
	}
	mux.Mu.Unlock()

	kubevirtVmMuxMu.Lock()
	if kubevirtVmMuxes[mux.Key] == mux {
		delete(kubevirtVmMuxes, mux.Key)
	}
	kubevirtVmMuxMu.Unlock()

	util.SilentClose(mux.Conn)

	for _, ch := range channels {
		if ch.Req.Ctx.Err() == nil {
			ch.Req.EmitStatus(tp.TerminalProtoStatusError, 1, "VM agent connection closed")
		}
		kubevirtCloseMuxChannel(ch)
	}
}

func kubevirtVmMuxWriteFrame(mux *kubevirtVmMux, op uint8, channelId uint16, payload []byte) error {
	encoded := ptyhost.PtyHostEncodeMuxFrame(op, channelId, payload)
	mux.WriteMu.Lock()
	defer mux.WriteMu.Unlock()
	_ = mux.Conn.SetWriteDeadline(time.Now().Add(15 * time.Second))
	return mux.Conn.WriteMessage(ws.BinaryMessage, encoded)
}

func kubevirtVmMuxReadLoop(mux *kubevirtVmMux) {
	defer kubevirtCloseVmMux(mux)

	for {
		msgType, raw, err := mux.Conn.ReadMessage()
		if err != nil {
			return
		}
		if msgType != ws.BinaryMessage {
			continue
		}
		frame, decErr := ptyhost.PtyHostDecodeMuxFrame(raw)
		if decErr != nil {
			continue
		}

		mux.Mu.Lock()
		ch := mux.Channels[frame.ChannelId]
		mux.Mu.Unlock()
		if ch == nil {
			continue
		}

		switch frame.Op {
		case ptyhost.PtyHostMuxOpReady:
			ready, rErr := ptyhost.PtyHostDecodeReadyPayload(frame.Payload)
			if rErr != nil {
				continue
			}
			flags := tp.TerminalProtoReadyPersistent | tp.TerminalProtoReadyVmAgentPty
			if ready.ResetRequired {
				flags |= tp.TerminalProtoReadyResetRequired
			}
			ch.Req.EmitReady(flags, ready.BaseOffset, ready.ReplayBytes, "")

		case ptyhost.PtyHostMuxOpOutput:
			if len(frame.Payload) > 0 {
				ch.Req.EmitData(frame.Payload)
			}

		case ptyhost.PtyHostMuxOpMeta:
			meta, mErr := ptyhost.PtyHostDecodeMetaPayload(frame.Payload)
			if mErr != nil {
				continue
			}
			if ch.Req.EmitMeta != nil {
				ch.Req.EmitMeta(meta.Busy, meta.CommandDone, meta.Command, meta.Cwd)
			}

		case ptyhost.PtyHostMuxOpExited:
			exitCode := ptyhost.PtyHostDecodeExitedPayload(frame.Payload)
			kubevirtVmMuxRemoveChannel(mux, frame.ChannelId)
			if ch.Req.Ctx.Err() == nil {
				ch.Req.EmitStatus(tp.TerminalProtoStatusExited, exitCode, "")
			}
			kubevirtCloseMuxChannel(ch)

		case ptyhost.PtyHostMuxOpError:
			kubevirtVmMuxRemoveChannel(mux, frame.ChannelId)
			if ch.Req.Ctx.Err() == nil {
				ch.Req.EmitStatus(tp.TerminalProtoStatusError, 1, string(frame.Payload))
			}
			kubevirtCloseMuxChannel(ch)
		}
	}
}

func kubevirtVmMuxRegisterChannel(mux *kubevirtVmMux, req *ctrl.PersistentPaneRequest) (*kubevirtMuxChannel, bool) {
	mux.Mu.Lock()
	defer mux.Mu.Unlock()

	if mux.Closed {
		return nil, false
	}
	if mux.IdleTimer != nil {
		mux.IdleTimer.Stop()
		mux.IdleTimer = nil
	}

	var assigned uint16
	for i := 0; i < 65535; i++ {
		candidate := mux.NextChannelId
		mux.NextChannelId++
		if candidate == 0 {
			continue
		}
		if _, used := mux.Channels[candidate]; !used {
			assigned = candidate
			break
		}
	}
	if assigned == 0 {
		return nil, false
	}

	ch := &kubevirtMuxChannel{
		Id:     assigned,
		Req:    req,
		Closed: make(chan struct{}),
	}
	mux.Channels[assigned] = ch
	return ch, true
}

func kubevirtVmMuxRemoveChannel(mux *kubevirtVmMux, channelId uint16) {
	mux.Mu.Lock()
	delete(mux.Channels, channelId)
	empty := len(mux.Channels) == 0 && !mux.Closed
	if empty && mux.IdleTimer == nil {
		mux.IdleTimer = time.AfterFunc(kubevirtMuxIdleClose, func() {
			mux.Mu.Lock()
			stillEmpty := len(mux.Channels) == 0 && !mux.Closed
			mux.IdleTimer = nil
			mux.Mu.Unlock()
			if stillEmpty {
				kubevirtCloseVmMux(mux)
			}
		})
	}
	mux.Mu.Unlock()
}

func kubevirtAttachPersistentPane(req *ctrl.PersistentPaneRequest) {
	if req.Job == nil || !ptyhost.PtyHostValidateHandle(req.Handle) {
		req.EmitStatus(tp.TerminalProtoStatusError, 1, "invalid VM terminal request")
		return
	}

	mux := kubevirtGetOrCreateVmMux(req.Ctx, req.Job.Id)
	if mux != nil {
		kubevirtAttachViaMux(req, mux)
		return
	}

	kubevirtAttachViaSerialFallback(req)
}

func kubevirtAttachViaMux(req *ctrl.PersistentPaneRequest, mux *kubevirtVmMux) {
	ch, ok := kubevirtVmMuxRegisterChannel(mux, req)
	if !ok {
		req.EmitStatus(tp.TerminalProtoStatusError, 1, "failed to allocate VM terminal channel")
		return
	}

	sourceHandle := req.SourceHandle
	if sourceHandle != "" && !ptyhost.PtyHostValidateHandle(sourceHandle) {
		sourceHandle = ""
	}
	attachPayload := ptyhost.PtyHostEncodeAttachPayload(ptyhost.PtyHostAttachPayload{
		Cols:         max(req.Cols, 1),
		Rows:         max(req.Rows, 1),
		Replay:       req.Replay,
		LastOffset:   req.LastOffset,
		Handle:       req.Handle,
		SourceHandle: sourceHandle,
	})

	attachErr := kubevirtVmMuxWriteFrame(mux, ptyhost.PtyHostMuxOpAttach, ch.Id, attachPayload)
	if attachErr != nil {
		kubevirtVmMuxRemoveChannel(mux, ch.Id)
		kubevirtCloseMuxChannel(ch)
		req.EmitStatus(tp.TerminalProtoStatusError, 1, "failed to attach to VM agent")
		return
	}

	defer func() {
		kubevirtVmMuxRemoveChannel(mux, ch.Id)
		kubevirtCloseMuxChannel(ch)
		_ = kubevirtVmMuxWriteFrame(mux, ptyhost.PtyHostMuxOpDetach, ch.Id, nil)
	}()

	for {
		select {
		case <-req.Ctx.Done():
			return
		case <-ch.Closed:
			return
		case ev, open := <-req.InputEvents:
			if !open {
				return
			}
			switch ev.Type {
			case ctrl.ShellEventTypeInput:
				writeErr := kubevirtVmMuxWriteFrame(mux, ptyhost.PtyHostMuxOpInput, ch.Id, []byte(ev.Data))
				if writeErr != nil {
					return
				}
			case ctrl.ShellEventTypeResize:
				if ev.Cols > 0 && ev.Rows > 0 {
					resizePayload := ptyhost.PtyHostEncodeResizePayload(ev.Cols, ev.Rows)
					writeErr := kubevirtVmMuxWriteFrame(mux, ptyhost.PtyHostMuxOpResize, ch.Id, resizePayload)
					if writeErr != nil {
						return
					}
				}
			case ctrl.ShellEventTypeCredit:
				if ev.Delta > 0 {
					creditPayload := ptyhost.PtyHostEncodeCreditPayload(ev.Delta)
					writeErr := kubevirtVmMuxWriteFrame(mux, ptyhost.PtyHostMuxOpCredit, ch.Id, creditPayload)
					if writeErr != nil {
						return
					}
				}
			case ctrl.ShellEventTypeTerminate:
				return
			}
		}
	}
}

func kubevirtAttachViaSerialFallback(req *ctrl.PersistentPaneRequest) {
	stdinReader, stdinWriter := io.Pipe()
	stdoutReader, stdoutWriter := io.Pipe()

	stream, err := KubevirtClient.
		VirtualMachineInstance(Namespace).
		SerialConsole(
			vmName(req.Job.Id, req.Rank),
			&kvapi.SerialConsoleOptions{},
		)
	if err != nil {
		log.Info("Failed to open serial console fallback for VM %s: %v", req.Job.Id, err)
		req.EmitStatus(tp.TerminalProtoStatusError, 1, "failed to connect to VM serial console")
		return
	}

	req.EmitReady(
		tp.TerminalProtoReadyFallbackShell,
		0,
		0,
		"VM agent is unavailable; using serial console fallback (non-persistent). Press Enter if no prompt appears.",
	)

	streamDone := make(chan error, 1)
	go func() {
		streamDone <- stream.Stream(kvapi.StreamOptions{
			In:  stdinReader,
			Out: stdoutWriter,
		})
	}()

	go func() {
		buf := make([]byte, 4*1024)
		for req.Ctx.Err() == nil {
			n, readErr := stdoutReader.Read(buf)
			if n > 0 {
				req.EmitData(buf[:n])
			}
			if readErr != nil {
				return
			}
		}
	}()

	for {
		select {
		case <-req.Ctx.Done():
			_ = stdinWriter.Close()
			_ = stdoutWriter.Close()
			return
		case <-streamDone:
			_ = stdinWriter.Close()
			_ = stdoutWriter.Close()
			if req.Ctx.Err() == nil {
				req.EmitStatus(tp.TerminalProtoStatusExited, 0, "")
			}
			return
		case ev, ok := <-req.InputEvents:
			if !ok {
				return
			}
			if ev.Type == ctrl.ShellEventTypeInput {
				_, writeErr := stdinWriter.Write([]byte(ev.Data))
				if writeErr != nil {
					return
				}
			}
		}
	}
}

func kubevirtKillPersistentPanes(job *orc.Job, handles []orc.TerminalSessionProviderHandleTarget) *util.HttpError {
	if job == nil || len(handles) == 0 {
		return nil
	}

	kubevirtVmMuxMu.Lock()
	existing, ok := kubevirtVmMuxes[job.Id]
	kubevirtVmMuxMu.Unlock()

	isOpen := false
	if ok {
		existing.Mu.Lock()
		isOpen = !existing.Closed
		existing.Mu.Unlock()
	}

	if isOpen {
		sentAll := true
		for _, target := range handles {
			if !ptyhost.PtyHostValidateHandle(target.BackendHandle) {
				continue
			}
			err := kubevirtVmMuxWriteFrame(existing, ptyhost.PtyHostMuxOpKill, 0, ptyhost.PtyHostEncodeKillPayload(target.BackendHandle))
			if err != nil {
				sentAll = false
				break
			}
		}
		if sentAll {
			return nil
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	conn := vmaRequestTty(ctx, job.Id)
	cancel()
	if conn == nil {
		return nil
	}
	defer util.SilentClose(conn)

	for _, target := range handles {
		if !ptyhost.PtyHostValidateHandle(target.BackendHandle) {
			continue
		}
		frame := ptyhost.PtyHostEncodeMuxFrame(ptyhost.PtyHostMuxOpKill, 0, ptyhost.PtyHostEncodeKillPayload(target.BackendHandle))
		_ = conn.WriteMessage(ws.BinaryMessage, frame)
	}
	return nil
}
