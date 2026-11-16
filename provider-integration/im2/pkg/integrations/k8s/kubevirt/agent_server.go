package kubevirt

import (
	"bytes"
	"context"
	"sync"
	"sync/atomic"
	"time"

	ws "github.com/gorilla/websocket"
	ctrl "ucloud.dk/pkg/controller"
	introspection "ucloud.dk/pkg/integrations/k8s/job-introspection"
	"ucloud.dk/pkg/integrations/k8s/shared"
	vmagent "ucloud.dk/pkg/integrations/k8s/vm-agent"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initAgentServer() {
	vmaSessions.ByJobId = map[string][]*vmaSession{}
	vmaSessions.PendingTtyByToken = map[string]*vmaPendingTty{}

	shared.SshKeyAddListener(func(username string, keys []orc.SshKey) {
		jobs := ctrl.JobsListServer()
		vmaSessions.Mu.RLock()
		for _, job := range jobs {
			if job.Owner.CreatedBy == username {
				for _, session := range vmaSessions.ByJobId[job.Id] {
					session.SendSshKeys.Store(true)
				}
			}
		}
		vmaSessions.Mu.RUnlock()
	})

	vmagent.VmaStream.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		c := info.WebSocket
		defer util.SilentClose(c)
		vmaServerHandleSession(c)
		return util.Empty{}, nil
	})

	vmagent.VmaTty.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		c := info.WebSocket

		_, msg, err := c.ReadMessage()
		if err != nil {
			util.SilentClose(c)
			return util.Empty{}, nil
		}

		ttyToken := string(msg)

		vmaSessions.Mu.Lock()
		pending, ok := vmaSessions.PendingTtyByToken[ttyToken]
		if ok {
			delete(vmaSessions.PendingTtyByToken, ttyToken)
		}
		vmaSessions.Mu.Unlock()

		if !ok {
			util.SilentClose(c)
			return util.Empty{}, nil
		}

		if c.WriteMessage(ws.TextMessage, []byte("OK")) != nil {
			util.SilentClose(c)
			return util.Empty{}, nil
		}

		select {
		case <-pending.Cancel:
			util.SilentClose(c)
		case pending.Conn <- c:
		}

		return util.Empty{}, nil
	})
}

const vmaStaleInterval = 60 * time.Second
const vmaWriteTimeout = 10 * time.Second

var vmaSessions struct {
	Mu                sync.RWMutex
	ByJobId           map[string][]*vmaSession
	PendingTtyByToken map[string]*vmaPendingTty
}

type vmaPendingTty struct {
	Conn   chan *ws.Conn
	Cancel chan struct{}
}

type vmaSession struct {
	Conn          *ws.Conn
	Ok            bool
	JobId         string
	SessionId     string
	SendSshKeys   atomic.Bool
	Mu            sync.RWMutex
	TtyRequests   []string
	LastHeartbeat atomic.Int64
}

func (s *vmaSession) TouchHeartbeat() {
	s.LastHeartbeat.Store(time.Now().Unix())
}

func (s *vmaSession) Stale() bool {
	return time.Since(time.Unix(s.LastHeartbeat.Load(), 0)) > vmaStaleInterval
}

func (s *vmaSession) SendBinary(data []byte) bool {
	if s.Ok {
		_ = s.Conn.SetWriteDeadline(time.Now().Add(vmaWriteTimeout))
		s.Ok = s.Conn.WriteMessage(ws.BinaryMessage, data) == nil
	}

	return s.Ok
}

func (s *vmaSession) SendText(data string) bool {
	if s.Ok {
		_ = s.Conn.SetWriteDeadline(time.Now().Add(vmaWriteTimeout))
		s.Ok = s.Conn.WriteMessage(ws.TextMessage, []byte(data)) == nil
	}

	return s.Ok
}

func vmaRequestTty(ctx context.Context, jobId string) *ws.Conn {
	ttyToken := util.SecureToken()
	pending := &vmaPendingTty{
		Conn:   make(chan *ws.Conn),
		Cancel: make(chan struct{}),
	}

	var tokenSession *vmaSession

	vmaSessions.Mu.Lock()
	sessions, ok := vmaSessions.ByJobId[jobId]
	if ok {
		freshSessions := make([]*vmaSession, 0, len(sessions))
		var selected *vmaSession
		for _, session := range sessions {
			if session.Stale() {
				continue
			}
			freshSessions = append(freshSessions, session)
			if selected == nil {
				selected = session
			}
		}

		if len(freshSessions) == 0 {
			delete(vmaSessions.ByJobId, jobId)
			ok = false
		} else {
			vmaSessions.ByJobId[jobId] = freshSessions
			selected.Mu.Lock()
			selected.TtyRequests = append(selected.TtyRequests, ttyToken)
			selected.Mu.Unlock()
			vmaSessions.PendingTtyByToken[ttyToken] = pending
			tokenSession = selected
		}
	}
	vmaSessions.Mu.Unlock()

	if !ok || len(sessions) == 0 {
		return nil
	}

	defer close(pending.Cancel)

	dropToken := func() {
		vmaSessions.Mu.Lock()
		delete(vmaSessions.PendingTtyByToken, ttyToken)
		if tokenSession != nil {
			tokenSession.Mu.Lock()
			filtered := tokenSession.TtyRequests[:0]
			for _, tok := range tokenSession.TtyRequests {
				if tok != ttyToken {
					filtered = append(filtered, tok)
				}
			}
			tokenSession.TtyRequests = filtered
			tokenSession.Mu.Unlock()
		}
		vmaSessions.Mu.Unlock()
	}

	select {
	case conn := <-pending.Conn:
		return conn
	case <-time.After(5 * time.Second):
		dropToken()
		return nil
	case <-ctx.Done():
		dropToken()
		return nil
	}
}

func vmaAuthenticate(token string) (string, util.Option[string], bool) {
	return introspection.Authenticate(token)
}

func vmaServerHandleSession(c *ws.Conn) {
	{
		_, authMsg, err := c.ReadMessage()
		if err != nil {
			return
		}

		s := &vmaSession{
			Conn:      c,
			Ok:        true,
			SessionId: util.SecureToken(),
		}
		s.TouchHeartbeat()

		jobId, srvToken, ok := vmaAuthenticate(string(authMsg))

		if !ok || srvToken.IsEmpty() {
			return
		}

		s.JobId = jobId
		s.SendText(srvToken.Get())

		vmaSessions.Mu.Lock()
		vmaSessions.ByJobId[jobId] = append(vmaSessions.ByJobId[jobId], s)
		vmaSessions.Mu.Unlock()

		defer func() {
			vmaSessions.Mu.Lock()
			var newSessions []*vmaSession
			for _, session := range vmaSessions.ByJobId[jobId] {
				if session.SessionId != s.SessionId {
					newSessions = append(newSessions, session)
				}
			}
			if len(newSessions) == 0 {
				delete(vmaSessions.ByJobId, jobId)
			} else {
				vmaSessions.ByJobId[jobId] = newSessions
			}
			vmaSessions.Mu.Unlock()
		}()

		vmaServerSendSshKeys(s)

		for {
			if !s.Ok {
				return
			}

			if s.SendSshKeys.CompareAndSwap(true, false) {
				vmaServerSendSshKeys(s)
			}

			s.Mu.Lock()
			ttyTokens := s.TtyRequests
			s.TtyRequests = nil
			s.Mu.Unlock()

			if len(ttyTokens) > 0 {
				for _, tok := range ttyTokens {
					rawBuf := &bytes.Buffer{}
					buf := util.NewBufferWithWriter(rawBuf)

					buf.WriteU8(uint8(vmagent.VmaSrvRequestTty))
					buf.WriteString(tok)

					s.SendBinary(rawBuf.Bytes())
				}
			}

			_ = c.SetReadDeadline(time.Now().Add(vmaStaleInterval))
			_, msg, err := c.ReadMessage()
			if err != nil {
				return
			}
			s.TouchHeartbeat()

			b := util.NewBuffer(bytes.NewBuffer(msg))
			switch vmagent.VmaAgentOpCode(b.ReadU8()) {
			case vmagent.VmaAgentHeartbeat:
				// Nothing to do
			}
		}
	}
}

func vmaServerSendSshKeys(s *vmaSession) {
	keyPage, err := orc.JobsControlBrowseSshKeys.Invoke(orc.JobsControlBrowseSshKeysRequest{JobId: s.JobId, FilterOwner: true})
	if err == nil {
		rawBuf := &bytes.Buffer{}
		buf := util.NewBufferWithWriter(rawBuf)
		buf.WriteU8(uint8(vmagent.VmaSrvSshKeys))
		buf.WriteS32(int32(len(keyPage.Items)))
		for _, key := range keyPage.Items {
			buf.WriteString(key.Specification.Key)
		}

		s.SendBinary(rawBuf.Bytes())
	}
}
