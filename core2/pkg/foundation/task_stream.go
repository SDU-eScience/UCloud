package foundation

import (
	"context"
	"encoding/json"
	"net/http"

	ws "github.com/gorilla/websocket"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// This file implements the task and notification stream used by the frontend. This system delivers both updates on
// tasks and notifications. On top of this, the system will deliver notifications about changes to an end-user's
// projects. The last one is crucial, as the frontend will need to renew their JWT for updated permissions.

func TaskListen(conn *ws.Conn) {
	taskStreamActiveConnections.Inc()
	taskStreamConnectionsTotal.Inc()

	defer func() {
		taskStreamActiveConnections.Dec()
		util.SilentClose(conn)
	}()

	var herr *util.HttpError
	var actor rpc.Actor
	var streamId string

	{
		// Handshake
		// -------------------------------------------------------------------------------------------------------------
		mtype, rawMessage, err := conn.ReadMessage()
		if err != nil || mtype != ws.TextMessage {
			return
		}

		var message rpc.WSRequestMessage[util.Empty]

		err = json.Unmarshal(rawMessage, &message)
		if err != nil {
			return
		}

		streamId = message.StreamId
		actor, herr = rpc.BearerAuthenticator(message.Bearer, message.Project.GetOrDefault(""))
		if herr != nil {
			return
		}

		if message.Call == "core2" {
			resp := rpc.WSResponseMessage[util.Empty]{
				Type:     "response",
				StreamId: streamId,
				Payload:  util.Empty{},
				Status:   http.StatusOK,
			}

			respBytes, _ := json.Marshal(resp)
			err := conn.WriteMessage(ws.TextMessage, respBytes)
			if err != nil {
				return
			}
		} else {
			return
		}

		// NOTE(Dan): Read message from client indicating that they are ready. Content is discarded on purpose.
		_, _, err = conn.ReadMessage()
		if err != nil {
			return
		}
	}

	{
		// Task initialization
		// -------------------------------------------------------------------------------------------------------------
		batch := fndapi.StreamMessageBatch{}

		result := TaskBrowse(actor, 250, util.OptNone[string]())
		for _, item := range result.Items {
			batch.Messages = append(batch.Messages, fndapi.StreamMessage{
				Type: fndapi.StreamMsgTask,
				Task: util.OptValue(util.Pointer(item)),
			})
		}

		if len(batch.Messages) > 0 {
			respBytes, _ := json.Marshal(batch)
			err := conn.WriteMessage(ws.TextMessage, respBytes)
			if err != nil {
				return
			}
		}
	}

	{
		// Notification initialization
		// -------------------------------------------------------------------------------------------------------------
		// NOTE(Dan): There is no notification initialization step. The frontend does this automatically.
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	tasks := TaskSubscribe(actor, ctx)
	notifications := NotificationsSubscribe(actor, ctx)
	projects := ProjectsSubscribe(actor, ctx)

	go func() {
		for {
			_, _, err := conn.ReadMessage()
			if err != nil {
				cancel()
				break
			}
		}
	}()

	for {
		select {
		case <-ctx.Done():
			return

		case msg, ok := <-tasks:
			if ok {
				batch := fndapi.StreamMessageBatch{Messages: []fndapi.StreamMessage{
					{
						Type: fndapi.StreamMsgTask,
						Task: util.OptValue(util.Pointer(msg)),
					},
				}}
				data, _ := json.Marshal(batch)

				err := conn.WriteMessage(ws.TextMessage, data)
				if err != nil {
					return
				}
			} else {
				return
			}

		case msg, ok := <-notifications:
			if ok {
				batch := fndapi.StreamMessageBatch{Messages: []fndapi.StreamMessage{
					{
						Type:         fndapi.StreamMsgNotification,
						Notification: util.OptValue(util.Pointer(msg)),
					},
				}}
				data, _ := json.Marshal(batch)

				err := conn.WriteMessage(ws.TextMessage, data)
				if err != nil {
					return
				}
			} else {
				return
			}

		case _, ok := <-projects:
			if ok {
				batch := fndapi.StreamMessageBatch{Messages: []fndapi.StreamMessage{
					{
						Type: fndapi.StreamMsgProjects,
					},
				}}
				data, _ := json.Marshal(batch)

				err := conn.WriteMessage(ws.TextMessage, data)
				if err != nil {
					return
				}
			} else {
				return
			}
		}
	}
}

// Metrics
// =====================================================================================================================

var (
	taskStreamActiveConnections = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud",
		Subsystem: "tasks",
		Name:      "active_connections",
		Help:      "Number of active connections.",
	})

	taskStreamConnectionsTotal = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "tasks",
		Name:      "connections_total",
		Help:      "Number of connections in total.",
	})
)
