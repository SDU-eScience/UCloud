package kubevirt

import (
	"bytes"
	"sync"
	"sync/atomic"

	ws "github.com/gorilla/websocket"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	vmagent "ucloud.dk/pkg/integrations/k8s/vm-agent"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initAgentServer() {
	vmaSessions.ByJobId = map[string][]*vmaSession{}

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
}

var vmaSessions struct {
	Mu      sync.RWMutex
	ByJobId map[string][]*vmaSession
}

type vmaSession struct {
	Conn        *ws.Conn
	Ok          bool
	JobId       string
	SessionId   string
	SendSshKeys atomic.Bool
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

		log.Info("Got token: %s", string(authMsg))
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
