package containers

import (
	"fmt"
	"io"
	"sync"
	"time"

	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/pkg/ptyhost"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	tp "ucloud.dk/shared/pkg/terminalproto"
	"ucloud.dk/shared/pkg/util"
)

const (
	containerUcloudPtyPath = "/opt/ucloud/ucloud-pty"
	containerMuxIdleClose  = 30 * time.Second
)

type containerMuxChannel struct {
	Id     uint16
	Req    *controller.PersistentPaneRequest
	Closed chan struct{}
	Once   sync.Once
}

type containerPodMux struct {
	Key           string
	JobId         string
	Rank          int
	Cmd           *shared.TerminalCmd
	StdinWriter   *io.PipeWriter
	WriteMu       sync.Mutex
	Mu            sync.Mutex
	Channels      map[uint16]*containerMuxChannel
	NextChannelId uint16
	Closed        bool
	IdleTimer     *time.Timer
	Done          chan struct{}
}

var containerPodMuxMu sync.Mutex
var containerPodMuxes = map[string]*containerPodMux{}

func containerMuxCloseChannel(ch *containerMuxChannel) {
	ch.Once.Do(func() {
		close(ch.Closed)
	})
}

func containerPodMuxKey(jobId string, rank int) string {
	return fmt.Sprintf("%s:%d", jobId, rank)
}

func containersGetOrCreatePodMux(jobId string, rank int) (*containerPodMux, error) {
	key := containerPodMuxKey(jobId, rank)

	containerPodMuxMu.Lock()
	existing, ok := containerPodMuxes[key]
	if ok {
		existing.Mu.Lock()
		isOpen := !existing.Closed
		if isOpen && existing.IdleTimer != nil {
			existing.IdleTimer.Stop()
			existing.IdleTimer = nil
		}
		existing.Mu.Unlock()
		if isOpen {
			containerPodMuxMu.Unlock()
			return existing, nil
		}
	}
	containerPodMuxMu.Unlock()

	sandbox, err := shared.TerminalOpenToJob(jobId, util.OptValue(rank))
	if err != nil {
		return nil, fmt.Errorf("%s", err.Why)
	}

	stdinReader, stdinWriter := io.Pipe()
	stdoutReader, stdoutWriter := io.Pipe()
	stderrReader, stderrWriter := io.Pipe()

	cmd := sandbox.Command(containerUcloudPtyPath, "mux")
	cmd.Stdin = stdinReader
	cmd.Stdout = stdoutWriter
	cmd.Stderr = stderrWriter
	cmd.TTY = false
	cmd.Start()
	if cmd.Err() != nil {
		_ = stdinWriter.Close()
		_ = stdoutWriter.Close()
		_ = stderrWriter.Close()
		return nil, cmd.Err()
	}

	mux := &containerPodMux{
		Key:           key,
		JobId:         jobId,
		Rank:          rank,
		Cmd:           cmd,
		StdinWriter:   stdinWriter,
		Channels:      map[uint16]*containerMuxChannel{},
		NextChannelId: 1,
		Done:          make(chan struct{}),
	}

	containerPodMuxMu.Lock()
	if prev, hasPrev := containerPodMuxes[key]; hasPrev {
		prev.Mu.Lock()
		prevOpen := !prev.Closed
		prev.Mu.Unlock()
		if prevOpen {
			containerPodMuxMu.Unlock()
			_ = stdinWriter.Close()
			cmd.Kill()
			_ = stdoutWriter.Close()
			_ = stderrWriter.Close()
			return prev, nil
		}
	}
	containerPodMuxes[key] = mux
	containerPodMuxMu.Unlock()

	go func() {
		_, _ = io.Copy(io.Discard, stderrReader)
	}()

	go func() {
		cmd.Wait()
		_ = stdoutWriter.Close()
		_ = stderrWriter.Close()
		containersClosePodMux(mux)
	}()

	go containersPodMuxReadLoop(mux, stdoutReader)

	return mux, nil
}

func containersClosePodMux(mux *containerPodMux) {
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
	channels := make([]*containerMuxChannel, 0, len(mux.Channels))
	for id, ch := range mux.Channels {
		channels = append(channels, ch)
		delete(mux.Channels, id)
	}
	mux.Mu.Unlock()

	containerPodMuxMu.Lock()
	if containerPodMuxes[mux.Key] == mux {
		delete(containerPodMuxes, mux.Key)
	}
	containerPodMuxMu.Unlock()

	_ = mux.StdinWriter.Close()
	close(mux.Done)

	for _, ch := range channels {
		if ch.Req.Ctx.Err() == nil {
			ch.Req.EmitStatus(tp.TerminalProtoStatusError, 1, "terminal mux disconnected")
		}
		containerMuxCloseChannel(ch)
	}
}

func containersPodMuxWriteFrame(mux *containerPodMux, op uint8, channelId uint16, payload []byte) error {
	mux.WriteMu.Lock()
	defer mux.WriteMu.Unlock()
	return ptyhost.PtyHostWriteMuxFrame(mux.StdinWriter, op, channelId, payload)
}

func containersPodMuxReadLoop(mux *containerPodMux, stdoutReader io.Reader) {
	defer containersClosePodMux(mux)

	for {
		frame, err := ptyhost.PtyHostReadMuxFrame(stdoutReader)
		if err != nil {
			return
		}

		mux.Mu.Lock()
		ch := mux.Channels[frame.ChannelId]
		mux.Mu.Unlock()
		if ch == nil {
			continue
		}

		switch frame.Op {
		case ptyhost.PtyHostMuxOpReady:
			ready, decErr := ptyhost.PtyHostDecodeReadyPayload(frame.Payload)
			if decErr != nil {
				continue
			}
			flags := tp.TerminalProtoReadyPersistent | tp.TerminalProtoReadyUcloudPty
			if ready.ResetRequired {
				flags |= tp.TerminalProtoReadyResetRequired
			}
			ch.Req.EmitReady(flags, ready.BaseOffset, ready.ReplayBytes, "")

		case ptyhost.PtyHostMuxOpOutput:
			if len(frame.Payload) > 0 {
				ch.Req.EmitData(frame.Payload)
			}

		case ptyhost.PtyHostMuxOpMeta:
			meta, decErr := ptyhost.PtyHostDecodeMetaPayload(frame.Payload)
			if decErr != nil {
				continue
			}
			if ch.Req.EmitMeta != nil {
				ch.Req.EmitMeta(meta.Busy, meta.CommandDone, meta.Command, meta.Cwd)
			}

		case ptyhost.PtyHostMuxOpExited:
			exitCode := ptyhost.PtyHostDecodeExitedPayload(frame.Payload)
			containersPodMuxRemoveChannel(mux, frame.ChannelId)
			if ch.Req.Ctx.Err() == nil {
				ch.Req.EmitStatus(tp.TerminalProtoStatusExited, exitCode, "")
			}
			containerMuxCloseChannel(ch)

		case ptyhost.PtyHostMuxOpError:
			containersPodMuxRemoveChannel(mux, frame.ChannelId)
			if ch.Req.Ctx.Err() == nil {
				ch.Req.EmitStatus(tp.TerminalProtoStatusError, 1, string(frame.Payload))
			}
			containerMuxCloseChannel(ch)
		}
	}
}

func containersPodMuxRegisterChannel(mux *containerPodMux, req *controller.PersistentPaneRequest) (*containerMuxChannel, bool) {
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

	ch := &containerMuxChannel{
		Id:     assigned,
		Req:    req,
		Closed: make(chan struct{}),
	}
	mux.Channels[assigned] = ch
	return ch, true
}

func containersPodMuxRemoveChannel(mux *containerPodMux, channelId uint16) {
	mux.Mu.Lock()
	delete(mux.Channels, channelId)
	empty := len(mux.Channels) == 0 && !mux.Closed
	if empty && mux.IdleTimer == nil {
		mux.IdleTimer = time.AfterFunc(containerMuxIdleClose, func() {
			mux.Mu.Lock()
			stillEmpty := len(mux.Channels) == 0 && !mux.Closed
			mux.IdleTimer = nil
			mux.Mu.Unlock()
			if stillEmpty {
				containersClosePodMux(mux)
			}
		})
	}
	mux.Mu.Unlock()
}

func containersAttachPersistentPane(req *controller.PersistentPaneRequest) {
	if req.Job == nil || !ptyhost.PtyHostValidateHandle(req.Handle) {
		req.EmitStatus(tp.TerminalProtoStatusError, 1, "invalid terminal pane handle")
		return
	}

	mux, err := containersGetOrCreatePodMux(req.Job.Id, req.Rank)
	if err != nil {
		log.Info("Failed to open ucloud-pty mux for job %s rank %d: %v", req.Job.Id, req.Rank, err)
		req.EmitStatus(tp.TerminalProtoStatusError, 1, "failed to start persistent terminal")
		return
	}

	ch, ok := containersPodMuxRegisterChannel(mux, req)
	if !ok {
		req.EmitStatus(tp.TerminalProtoStatusError, 1, "failed to allocate terminal channel")
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

	attachErr := containersPodMuxWriteFrame(mux, ptyhost.PtyHostMuxOpAttach, ch.Id, attachPayload)
	if attachErr != nil {
		containersPodMuxRemoveChannel(mux, ch.Id)
		containerMuxCloseChannel(ch)
		req.EmitStatus(tp.TerminalProtoStatusError, 1, "failed to attach persistent terminal")
		return
	}

	defer func() {
		containersPodMuxRemoveChannel(mux, ch.Id)
		containerMuxCloseChannel(ch)
		_ = containersPodMuxWriteFrame(mux, ptyhost.PtyHostMuxOpDetach, ch.Id, nil)
	}()

	for {
		select {
		case <-req.Ctx.Done():
			return
		case <-ch.Closed:
			return
		case event, open := <-req.InputEvents:
			if !open {
				return
			}
			switch event.Type {
			case controller.ShellEventTypeInput:
				writeErr := containersPodMuxWriteFrame(mux, ptyhost.PtyHostMuxOpInput, ch.Id, []byte(event.Data))
				if writeErr != nil {
					return
				}
			case controller.ShellEventTypeResize:
				if event.Cols > 0 && event.Rows > 0 {
					resizePayload := ptyhost.PtyHostEncodeResizePayload(event.Cols, event.Rows)
					writeErr := containersPodMuxWriteFrame(mux, ptyhost.PtyHostMuxOpResize, ch.Id, resizePayload)
					if writeErr != nil {
						return
					}
				}
			case controller.ShellEventTypeCredit:
				if event.Delta > 0 {
					creditPayload := ptyhost.PtyHostEncodeCreditPayload(event.Delta)
					writeErr := containersPodMuxWriteFrame(mux, ptyhost.PtyHostMuxOpCredit, ch.Id, creditPayload)
					if writeErr != nil {
						return
					}
				}
			case controller.ShellEventTypeTerminate:
				return
			}
		}
	}
}

func containersKillPersistentPanes(job *orc.Job, handles []orc.TerminalSessionProviderHandleTarget) *util.HttpError {
	if job == nil || len(handles) == 0 {
		return nil
	}

	byRank := map[int][]string{}
	for _, target := range handles {
		if !ptyhost.PtyHostValidateHandle(target.BackendHandle) {
			continue
		}
		byRank[target.Rank] = append(byRank[target.Rank], target.BackendHandle)
	}

	for rank, rankHandles := range byRank {
		key := containerPodMuxKey(job.Id, rank)
		containerPodMuxMu.Lock()
		existing, ok := containerPodMuxes[key]
		containerPodMuxMu.Unlock()

		isOpen := false
		if ok {
			existing.Mu.Lock()
			isOpen = !existing.Closed
			existing.Mu.Unlock()
		}

		if isOpen {
			sentAll := true
			for _, h := range rankHandles {
				err := containersPodMuxWriteFrame(existing, ptyhost.PtyHostMuxOpKill, 0, ptyhost.PtyHostEncodeKillPayload(h))
				if err != nil {
					sentAll = false
					break
				}
			}
			if sentAll {
				continue
			}
		}

		sandbox, err := shared.TerminalOpenToJob(job.Id, util.OptValue(rank))
		if err != nil {
			continue
		}

		args := append([]string{"kill"}, rankHandles...)
		cmd := sandbox.Command(containerUcloudPtyPath, args...)
		cmd.TTY = false
		cmd.Start()
		if cmd.Err() == nil {
			done := make(chan struct{})
			go func() {
				cmd.Wait()
				close(done)
			}()
			select {
			case <-done:
			case <-time.After(5 * time.Second):
				cmd.Kill()
			}
		}
	}

	return nil
}
