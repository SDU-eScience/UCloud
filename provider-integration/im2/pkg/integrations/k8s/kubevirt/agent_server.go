package kubevirt

import (
	"bytes"
	"sync"
	"sync/atomic"
	"time"

	ws "github.com/gorilla/websocket"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	vmagent "ucloud.dk/pkg/integrations/k8s/vm-agent"
	db "ucloud.dk/shared/pkg/database"
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
	Conn        *ws.Conn
	Ok          bool
	JobId       string
	SessionId   string
	SendSshKeys atomic.Bool
	Mu          sync.RWMutex
	TtyRequests []string
}

func (s *vmaSession) SendBinary(data []byte) bool {
	if s.Ok {
		s.Ok = s.Conn.WriteMessage(ws.BinaryMessage, data) == nil
	}

	return s.Ok
}

func (s *vmaSession) SendText(data string) bool {
	if s.Ok {
		s.Ok = s.Conn.WriteMessage(ws.TextMessage, []byte(data)) == nil
	}

	return s.Ok
}

func vmaRequestTty(jobId string) *ws.Conn {
	ttyToken := util.SecureToken()
	pending := &vmaPendingTty{
		Conn:   make(chan *ws.Conn),
		Cancel: make(chan struct{}),
	}

	vmaSessions.Mu.Lock()
	sessions, ok := vmaSessions.ByJobId[jobId]
	if ok && len(sessions) > 0 {
		session := sessions[0]
		session.Mu.Lock()
		session.TtyRequests = append(session.TtyRequests, ttyToken)
		session.Mu.Unlock()
		vmaSessions.PendingTtyByToken[ttyToken] = pending
	}
	vmaSessions.Mu.Unlock()

	if !ok || len(sessions) == 0 {
		return nil
	}

	defer close(pending.Cancel)

	select {
	case conn := <-pending.Conn:
		return conn
	case <-time.After(5 * time.Second):
		vmaSessions.Mu.Lock()
		delete(vmaSessions.PendingTtyByToken, ttyToken)
		vmaSessions.Mu.Unlock()
		return nil
	}
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

		jobId, srvToken, ok := db.NewTx3(func(tx *db.Transaction) (string, string, bool) {
			row, ok := db.Get[struct {
				JobId    string
				SrvToken string
			}](
				tx,
				`
					select job_id, srv_token
					from k8s.vmagents
					where agent_token = :agent_token
			    `,
				db.Params{
					"agent_token": string(authMsg),
				},
			)

			if ok {
				return row.JobId, row.SrvToken, true
			} else {
				return "", "", false
			}
		})

		if !ok {
			return
		}

		s.JobId = jobId
		s.SendText(srvToken)

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
				vmaSessions.ByJobId[jobId] = newSessions
			} else {
				delete(vmaSessions.ByJobId, jobId)
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

			_, msg, err := c.ReadMessage()
			if err != nil {
				return
			}

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
