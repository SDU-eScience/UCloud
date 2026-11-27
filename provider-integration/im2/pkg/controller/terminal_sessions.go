package controller

import (
	"context"
	"fmt"
	"net/http"
	"sync"
	"time"

	ws "github.com/gorilla/websocket"
	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	tp "ucloud.dk/shared/pkg/terminalproto"
	"ucloud.dk/shared/pkg/util"
)

type PersistentPaneRequest struct {
	Ctx            context.Context
	Job            *orcapi.Job
	Rank           int
	Handle         string
	SourceHandle   string
	Cols           int
	Rows           int
	Replay         bool
	LastOffset     uint64
	UCloudUsername string
	InputEvents    chan ShellEvent
	EmitReady      func(flags uint8, baseOffset uint64, replayBytes uint32, warning string)
	EmitData       func(data []byte)
	EmitMeta       func(busy bool, commandDone bool, command string, cwd string)
	EmitStatus     func(statusCode uint8, exitCode int32, message string)
}

type terminalConnectTokenEntry struct {
	SessionId string
	Job       orcapi.Job
	Username  string
	ExpiresAt time.Time
}

type terminalWsPane struct {
	ChannelId   uint16
	Rank        int
	Handle      string
	Cancel      context.CancelFunc
	InputEvents chan ShellEvent
	DataQueue   chan []byte
	CreditMu    sync.Mutex
	CreditCond  *sync.Cond
	Credit      int64
	Closed      bool
}

type terminalWsSession struct {
	SessionId string
	Job       orcapi.Job
	Username  string
	Conn      *ws.Conn
	WriteMu   sync.Mutex
	PanesMu   sync.Mutex
	Panes     map[uint16]*terminalWsPane
	Cancel    context.CancelFunc
}

var terminalTokensMu sync.Mutex
var terminalConnectTokens = map[string]terminalConnectTokenEntry{}

var terminalActiveWsMu sync.Mutex
var terminalActiveWsBySession = map[string]*terminalWsSession{}

var terminalWsUpgrader = ws.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		return true
	},
}

const terminalTokenTtl = 60 * time.Second
const terminalCoalesceWindow = 3 * time.Millisecond
const terminalCoalesceMaxBytes = 32 * 1024

func initTerminalSessions() {
	if RunsServerCode() {
		orcapi.JobsProviderTerminalSessionConnect.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionProviderConnectRequest) (orcapi.TerminalSessionProviderConnectResponse, *util.HttpError) {
			if Jobs.AttachPersistentPane == nil {
				return orcapi.TerminalSessionProviderConnectResponse{}, util.HttpErr(http.StatusBadRequest, "persistent terminal sessions are not supported by this provider")
			}

			terminalCleanupExpiredTokens()

			tok := util.RandomToken(32)
			terminalTokensMu.Lock()
			terminalConnectTokens[tok] = terminalConnectTokenEntry{
				SessionId: request.SessionId,
				Job:       request.Job,
				Username:  request.Username,
				ExpiresAt: time.Now().Add(terminalTokenTtl),
			}
			terminalTokensMu.Unlock()

			return orcapi.TerminalSessionProviderConnectResponse{
				Token:          tok,
				DomainOverride: cfg.Provider.Hosts.SelfPublic.ToWebSocketUrl(),
			}, nil
		})

		orcapi.JobsProviderTerminalKillHandles.Handler(func(info rpc.RequestInfo, request orcapi.TerminalSessionProviderKillHandlesRequest) (util.Empty, *util.HttpError) {
			if Jobs.KillPersistentPanes == nil {
				return util.Empty{}, nil
			}
			err := Jobs.KillPersistentPanes(&request.Job, request.Handles)
			return util.Empty{}, err
		})
	}

	if RunsUserCode() {
		rpc.DefaultServer.Mux.HandleFunc(
			fmt.Sprintf("/ucloud/%v/terminal-ws", cfg.Provider.Id),
			terminalWsHandleHttp,
		)
	}
}

func terminalCleanupExpiredTokens() {
	now := time.Now()
	terminalTokensMu.Lock()
	for k, entry := range terminalConnectTokens {
		if now.After(entry.ExpiresAt) {
			delete(terminalConnectTokens, k)
		}
	}
	terminalTokensMu.Unlock()
}

func terminalConsumeToken(token string) (terminalConnectTokenEntry, bool) {
	terminalTokensMu.Lock()
	defer terminalTokensMu.Unlock()

	entry, ok := terminalConnectTokens[token]
	if !ok {
		return terminalConnectTokenEntry{}, false
	}
	delete(terminalConnectTokens, token)
	if time.Now().After(entry.ExpiresAt) {
		return terminalConnectTokenEntry{}, false
	}
	return entry, true
}

func terminalWsWriteBinary(session *terminalWsSession, payload []byte) error {
	session.WriteMu.Lock()
	defer session.WriteMu.Unlock()
	_ = session.Conn.SetWriteDeadline(time.Now().Add(15 * time.Second))
	return session.Conn.WriteMessage(ws.BinaryMessage, payload)
}

func terminalWsHandleHttp(writer http.ResponseWriter, request *http.Request) {
	if !checkEnvoySecret(writer, request) {
		return
	}

	conn, err := terminalWsUpgrader.Upgrade(writer, request, nil)
	if err != nil {
		log.Info("Terminal WS upgrade failed: %v", err)
		return
	}
	defer util.SilentClose(conn)

	_ = conn.SetReadDeadline(time.Now().Add(15 * time.Second))
	msgType, rawHello, readErr := conn.ReadMessage()
	if readErr != nil || msgType != ws.BinaryMessage {
		return
	}
	_ = conn.SetReadDeadline(time.Time{})

	helloFrame, frameErr := tp.TerminalProtoDecodeHeader(rawHello)
	if frameErr != nil || helloFrame.Opcode != tp.TerminalProtoOpHello {
		return
	}

	hello, helloErr := tp.TerminalProtoDecodeHello(helloFrame.Payload)
	if helloErr != nil || hello.Version != tp.TerminalProtoVersion {
		return
	}

	tokenEntry, ok := terminalConsumeToken(hello.Token)
	if !ok {
		return
	}

	sessionCtx, sessionCancel := context.WithCancel(context.Background())
	session := &terminalWsSession{
		SessionId: tokenEntry.SessionId,
		Job:       tokenEntry.Job,
		Username:  tokenEntry.Username,
		Conn:      conn,
		Panes:     map[uint16]*terminalWsPane{},
		Cancel:    sessionCancel,
	}

	terminalActiveWsMu.Lock()
	previous := terminalActiveWsBySession[session.SessionId]
	terminalActiveWsBySession[session.SessionId] = session
	terminalActiveWsMu.Unlock()

	if previous != nil {
		replacedFrame := tp.TerminalProtoEncodePaneStatus(0, tp.TerminalProtoStatusReplaced, 0, "Opened in another tab")
		_ = terminalWsWriteBinary(previous, replacedFrame)
		previous.Cancel()
		util.SilentClose(previous.Conn)
	}

	defer func() {
		sessionCancel()
		terminalWsDetachAllPanes(session)
		terminalActiveWsMu.Lock()
		if terminalActiveWsBySession[session.SessionId] == session {
			delete(terminalActiveWsBySession, session.SessionId)
		}
		terminalActiveWsMu.Unlock()
	}()

	ackFrame := tp.TerminalProtoEncodeHelloAck(1, tp.TerminalProtoInitialCredit)
	writeErr := terminalWsWriteBinary(session, ackFrame)
	if writeErr != nil {
		return
	}

	for util.IsAlive && sessionCtx.Err() == nil {
		messageType, raw, err := conn.ReadMessage()
		if err != nil {
			break
		}
		if messageType != ws.BinaryMessage {
			continue
		}

		frame, decErr := tp.TerminalProtoDecodeHeader(raw)
		if decErr != nil {
			continue
		}

		switch frame.Opcode {
		case tp.TerminalProtoOpPing:
			_ = terminalWsWriteBinary(session, tp.TerminalProtoEncodePong(frame.Payload))

		case tp.TerminalProtoOpPaneAttach:
			attach, err := tp.TerminalProtoDecodePaneAttach(frame.Payload)
			if err != nil || frame.ChannelId == 0 {
				continue
			}
			terminalWsAttachPane(sessionCtx, session, frame.ChannelId, attach)

		case tp.TerminalProtoOpPaneData:
			pane := terminalWsGetPane(session, frame.ChannelId)
			if pane == nil {
				continue
			}
			dataCopy := string(frame.Payload)
			select {
			case pane.InputEvents <- ShellEvent{
				Type: ShellEventTypeInput,
				ShellEventInput: ShellEventInput{
					Data: dataCopy,
				},
			}:
			default:
			}

		case tp.TerminalProtoOpPaneResize:
			cols, rows, err := tp.TerminalProtoDecodeResize(frame.Payload)
			if err != nil {
				continue
			}
			pane := terminalWsGetPane(session, frame.ChannelId)
			if pane == nil {
				continue
			}
			select {
			case pane.InputEvents <- ShellEvent{
				Type: ShellEventTypeResize,
				ShellEventResize: ShellEventResize{
					Cols: int(cols),
					Rows: int(rows),
				},
			}:
			default:
			}

		case tp.TerminalProtoOpPaneDetach:
			terminalWsDetachPane(session, frame.ChannelId, false)

		case tp.TerminalProtoOpPaneShutdown:
			terminalWsDetachPane(session, frame.ChannelId, true)

		case tp.TerminalProtoOpPaneCredit:
			delta, err := tp.TerminalProtoDecodeCredit(frame.Payload)
			if err != nil {
				continue
			}
			pane := terminalWsGetPane(session, frame.ChannelId)
			if pane == nil {
				continue
			}
			pane.CreditMu.Lock()
			pane.Credit += int64(delta)
			pane.CreditCond.Broadcast()
			pane.CreditMu.Unlock()
			select {
			case pane.InputEvents <- ShellEvent{
				Type: ShellEventTypeCredit,
				ShellEventCredit: ShellEventCredit{
					Delta: delta,
				},
			}:
			default:
			}
		}
	}
}

func terminalWsGetPane(session *terminalWsSession, channelId uint16) *terminalWsPane {
	session.PanesMu.Lock()
	defer session.PanesMu.Unlock()
	return session.Panes[channelId]
}

func terminalWsAttachPane(
	sessionCtx context.Context,
	session *terminalWsSession,
	channelId uint16,
	attach tp.TerminalProtoPaneAttach,
) {
	terminalWsDetachPane(session, channelId, false)

	paneCtx, paneCancel := context.WithCancel(sessionCtx)
	pane := &terminalWsPane{
		ChannelId:   channelId,
		Rank:        int(attach.Rank),
		Handle:      attach.Handle,
		Cancel:      paneCancel,
		InputEvents: make(chan ShellEvent, 256),
		DataQueue:   make(chan []byte, 256),
		Credit:      int64(tp.TerminalProtoInitialCredit),
	}
	pane.CreditCond = sync.NewCond(&pane.CreditMu)

	session.PanesMu.Lock()
	session.Panes[channelId] = pane
	session.PanesMu.Unlock()

	go terminalWsPumpPaneOutput(paneCtx, session, pane)

	jobCopy := session.Job
	req := &PersistentPaneRequest{
		Ctx:            paneCtx,
		Job:            &jobCopy,
		Rank:           int(attach.Rank),
		Handle:         attach.Handle,
		SourceHandle:   attach.SourceHandle,
		Cols:           int(attach.Cols),
		Rows:           int(attach.Rows),
		Replay:         (attach.Flags & tp.TerminalProtoAttachFlagReplay) != 0,
		LastOffset:     attach.LastOffset,
		UCloudUsername: session.Username,
		InputEvents:    pane.InputEvents,
		EmitReady: func(flags uint8, baseOffset uint64, replayBytes uint32, warning string) {
			frame := tp.TerminalProtoEncodePaneReady(channelId, flags, baseOffset, replayBytes, warning)
			_ = terminalWsWriteBinary(session, frame)
		},
		EmitData: func(data []byte) {
			if len(data) == 0 || paneCtx.Err() != nil {
				return
			}
			copied := make([]byte, len(data))
			copy(copied, data)
			select {
			case pane.DataQueue <- copied:
			case <-paneCtx.Done():
			}
		},
		EmitMeta: func(busy bool, commandDone bool, command string, cwd string) {
			if paneCtx.Err() != nil {
				return
			}
			frame := tp.TerminalProtoEncodePaneMeta(channelId, busy, commandDone, command, cwd)
			_ = terminalWsWriteBinary(session, frame)
		},
		EmitStatus: func(statusCode uint8, exitCode int32, message string) {
			frame := tp.TerminalProtoEncodePaneStatus(channelId, statusCode, exitCode, message)
			_ = terminalWsWriteBinary(session, frame)
		},
	}

	go func() {
		defer terminalWsClosePaneQueue(pane)
		if Jobs.AttachPersistentPane == nil {
			req.EmitStatus(tp.TerminalProtoStatusError, 1, "persistent terminals are not supported")
			return
		}
		Jobs.AttachPersistentPane(req)
	}()
}

func terminalWsClosePaneQueue(pane *terminalWsPane) {
	pane.CreditMu.Lock()
	if !pane.Closed {
		pane.Closed = true
		close(pane.DataQueue)
		pane.CreditCond.Broadcast()
	}
	pane.CreditMu.Unlock()
}

func terminalWsPumpPaneOutput(paneCtx context.Context, session *terminalWsSession, pane *terminalWsPane) {
	batch := make([]byte, 0, terminalCoalesceMaxBytes)
	timer := time.NewTimer(terminalCoalesceWindow)
	if !timer.Stop() {
		select {
		case <-timer.C:
		default:
		}
	}
	defer timer.Stop()

	flush := func() bool {
		if len(batch) == 0 {
			return true
		}

		pane.CreditMu.Lock()
		for pane.Credit <= 0 && !pane.Closed && paneCtx.Err() == nil {
			pane.CreditCond.Wait()
		}
		if pane.Closed || paneCtx.Err() != nil {
			pane.CreditMu.Unlock()
			return false
		}
		pane.Credit -= int64(len(batch))
		pane.CreditMu.Unlock()

		frame := tp.TerminalProtoEncodePaneData(pane.ChannelId, batch)
		batch = batch[:0]
		err := terminalWsWriteBinary(session, frame)
		return err == nil
	}

	go func() {
		<-paneCtx.Done()
		pane.CreditMu.Lock()
		pane.CreditCond.Broadcast()
		pane.CreditMu.Unlock()
	}()

	timerActive := false
	for {
		if !timerActive {
			select {
			case <-paneCtx.Done():
				return
			case chunk, ok := <-pane.DataQueue:
				if !ok {
					_ = flush()
					return
				}
				batch = append(batch, chunk...)
				if len(batch) >= terminalCoalesceMaxBytes {
					if !flush() {
						return
					}
				} else {
					timer.Reset(terminalCoalesceWindow)
					timerActive = true
				}
			}
		} else {
			select {
			case <-paneCtx.Done():
				return
			case chunk, ok := <-pane.DataQueue:
				if !ok {
					_ = flush()
					return
				}
				batch = append(batch, chunk...)
				if len(batch) >= terminalCoalesceMaxBytes {
					if !timer.Stop() {
						select {
						case <-timer.C:
						default:
						}
					}
					timerActive = false
					if !flush() {
						return
					}
				}
			case <-timer.C:
				timerActive = false
				if !flush() {
					return
				}
			}
		}
	}
}

func terminalWsDetachPane(session *terminalWsSession, channelId uint16, killPersistent bool) {
	session.PanesMu.Lock()
	pane, ok := session.Panes[channelId]
	if ok {
		delete(session.Panes, channelId)
	}
	session.PanesMu.Unlock()

	if !ok {
		return
	}

	pane.Cancel()
	terminalWsClosePaneQueue(pane)

	if killPersistent && Jobs.KillPersistentPanes != nil {
		jobCopy := session.Job
		go func() {
			_ = Jobs.KillPersistentPanes(&jobCopy, []orcapi.TerminalSessionProviderHandleTarget{
				{
					Rank:          pane.Rank,
					BackendHandle: pane.Handle,
				},
			})
		}()
	}
}

func terminalWsDetachAllPanes(session *terminalWsSession) {
	session.PanesMu.Lock()
	panes := make([]*terminalWsPane, 0, len(session.Panes))
	for id, pane := range session.Panes {
		panes = append(panes, pane)
		delete(session.Panes, id)
	}
	session.PanesMu.Unlock()

	for _, pane := range panes {
		pane.Cancel()
		terminalWsClosePaneQueue(pane)
	}
}
