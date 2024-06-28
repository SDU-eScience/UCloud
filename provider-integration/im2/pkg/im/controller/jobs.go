package controller

import (
	"encoding/json"
	"fmt"
	ws "github.com/gorilla/websocket"
	"net/http"
	"strings"
	"sync"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var Jobs JobsService

type JobsService struct {
	Submit           func(request JobSubmitRequest) (util.Option[string], error)
	Terminate        func(request JobTerminateRequest) error
	Extend           func(request JobExtendRequest) error
	RetrieveProducts func() []orc.JobSupport
	Follow           func(session *FollowJobSession)
}

type FollowJobSession struct {
	Id       string
	Alive    bool
	Job      *orc.Job
	EmitLogs func(rank int, stdout, stderr util.Option[string])
}

type JobTerminateRequest struct {
	Job *orc.Job
}

type JobSubmitRequest struct {
	JobToSubmit *orc.Job
}

type JobExtendRequest struct {
	Job           *orc.Job
	RequestedTime orc.SimpleDuration
}

func controllerJobs(mux *http.ServeMux) {
	jobContext := fmt.Sprintf("/ucloud/%v/jobs/", cfg.Provider.Id)

	creationUrl, _ := strings.CutSuffix(jobContext, "/")
	mux.HandleFunc(creationUrl, HttpUpdateHandler[fnd.BulkRequest[*orc.Job]](
		0,
		func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[*orc.Job]) {
			var errors []error
			var providerIds []*fnd.FindByStringId

			for _, item := range request.Items {
				providerGeneratedId, err := Jobs.Submit(JobSubmitRequest{
					JobToSubmit: item,
				})

				if providerGeneratedId.IsSet() && err == nil {
					providerIds = append(providerIds, &fnd.FindByStringId{Id: providerGeneratedId.Get()})
				} else {
					providerIds = append(providerIds, nil)
				}

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) == 1 && len(request.Items) == 1 {
				sendError(w, errors[0])
			} else {
				var response fnd.BulkResponse[*fnd.FindByStringId]
				response.Responses = providerIds
				sendResponseOrError(w, response, nil)
			}
		}),
	)

	mux.HandleFunc(jobContext+"terminate", HttpUpdateHandler[fnd.BulkRequest[*orc.Job]](
		0,
		func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[*orc.Job]) {
			var errors []error

			for _, item := range request.Items {
				err := Jobs.Terminate(JobTerminateRequest{
					Job: item,
				})

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				sendError(w, errors[0])
			} else {
				var response fnd.BulkResponse[util.Empty]
				for i := 0; i < len(request.Items); i++ {
					response.Responses = append(response.Responses, util.Empty{})
				}

				sendResponseOrError(w, response, nil)
			}
		}),
	)
	type extendRequest struct {
		Job           *orc.Job           `json:"jobId"`
		RequestedTime orc.SimpleDuration `json:"requestedTime"`
	}

	mux.HandleFunc(jobContext+"extend", HttpUpdateHandler[fnd.BulkRequest[extendRequest]](
		0,
		func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[extendRequest]) {
			var errors []error

			for _, item := range request.Items {
				err := Jobs.Extend(JobExtendRequest{
					Job:           item.Job,
					RequestedTime: item.RequestedTime,
				})

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				sendError(w, errors[0])
			} else {
				var response fnd.BulkResponse[util.Empty]
				for i := 0; i < len(request.Items); i++ {
					response.Responses = append(response.Responses, util.Empty{})
				}

				sendResponseOrError(w, response, nil)
			}
		}),
	)

	mux.HandleFunc(jobContext+"retrieveProducts", HttpRetrieveHandler[util.Empty](
		0,
		func(w http.ResponseWriter, r *http.Request, _ util.Empty) {
			products := Jobs.RetrieveProducts()
			sendResponseOrError(
				w,
				fnd.BulkResponse[orc.JobSupport]{
					Responses: products,
				},
				nil,
			)
		}),
	)

	wsUpgrader := ws.Upgrader{
		ReadBufferSize:  1024 * 4,
		WriteBufferSize: 1024 * 4,
	}
	followCall := fmt.Sprintf("jobs.provider.%v.follow", cfg.Provider.Id)
	mux.HandleFunc(
		fmt.Sprintf("/ucloud/jobs.provider.%v/websocket", cfg.Provider.Id),
		func(writer http.ResponseWriter, request *http.Request) {
			conn, err := wsUpgrader.Upgrade(writer, request, nil)
			defer util.SilentCloseIfOk(conn, err)
			if err != nil {
				log.Debug("Expected a websocket connection, but couldn't upgrade: %v", err)
				return
			}

			log.Info("We are now listening for logs (probably)")

			for util.IsAlive {
				messageType, data, err := conn.ReadMessage()

				if err != nil {
					break
				}

				if messageType != ws.TextMessage {
					log.Debug("Only handling text messages but got a %v", messageType)
					continue
				}

				requestMessage := WebSocketRequest{}
				err = json.Unmarshal(data, &requestMessage)
				if err != nil {
					log.Info("Failed to unmarshal websocket message: %v", err)
					break
				}

				if requestMessage.Call != followCall {
					log.Info("Unexpected call on stream: %v", requestMessage.Call)
					break
				}

				followRequest := jobsProviderFollowRequest{}
				err = json.Unmarshal(requestMessage.Payload, &followRequest)
				if err != nil {
					log.Info("Failed to unmarshal follow message: %v", err)
					break
				}

				switch followRequest.Type {
				case jobsProviderFollowRequestTypeInit:
					session := createFollowSession(requestMessage.StreamId, conn, followRequest.Job)
					go func() {
						Jobs.Follow(session)
						session.Alive = false

						dummy := jobsProviderFollowResponse{
							StreamId: session.Id,
							Rank:     0,
							Stdout:   util.Option[string]{},
							Stderr:   util.Option[string]{},
						}
						dummyData, _ := json.Marshal(dummy)
						_ = conn.WriteJSON(WebSocketResponseFin{
							Type:     "response",
							Status:   http.StatusOK,
							StreamId: requestMessage.StreamId,
							Payload:  dummyData,
						})
					}()

				case jobsProviderFollowRequestTypeCancel:
					followSessionsMutex.Lock()
					session, ok := followSessions[followRequest.StreamId]
					if ok {
						session.Alive = false
					}
					followSessionsMutex.Unlock()

					dummy := jobsProviderFollowResponse{
						StreamId: session.Id,
						Rank:     0,
						Stdout:   util.Option[string]{},
						Stderr:   util.Option[string]{},
					}
					dummyData, _ := json.Marshal(dummy)
					_ = conn.WriteJSON(WebSocketResponseFin{
						Type:     "response",
						Status:   http.StatusOK,
						StreamId: requestMessage.StreamId,
						Payload:  dummyData,
					})
				}
			}
		},
	)
}

type WebSocketRequest struct {
	Call         string              `json:"call"`
	StreamId     string              `json:"streamId"`
	Payload      json.RawMessage     `json:"payload"`
	Bearer       util.Option[string] `json:"bearer"`
	CausedBy     util.Option[string] `json:"causedBy"`
	Project      util.Option[string] `json:"project"`
	SignedIntent util.Option[string] `json:"signedIntent"`
}

type WebSocketResponseFin struct {
	Type     string          `json:"type"` // must be "response"
	Status   int             `json:"status"`
	StreamId string          `json:"streamId"`
	Payload  json.RawMessage `json:"payload"`
}

type WebSocketResponseMessage struct {
	Type     string          `json:"type"` // must be "message"
	Payload  json.RawMessage `json:"payload"`
	StreamId string          `json:"streamId"`
}

type jobsProviderFollowRequestType string

const (
	jobsProviderFollowRequestTypeInit   jobsProviderFollowRequestType = "init"
	jobsProviderFollowRequestTypeCancel jobsProviderFollowRequestType = "cancel"
)

type jobsProviderFollowRequest struct {
	Type     jobsProviderFollowRequestType `json:"type"`
	StreamId string                        `json:"streamId,omitempty"` // cancel only
	Job      *orc.Job                      `json:"job,omitempty"`      // init only
}

type jobsProviderFollowResponse struct {
	StreamId string              `json:"streamId"`
	Rank     int                 `json:"rank"`
	Stdout   util.Option[string] `json:"stdout"`
	Stderr   util.Option[string] `json:"stderr"`
}

func createFollowSession(wsStreamId string, mainConnection *ws.Conn, job *orc.Job) *FollowJobSession {
	cleanupFollowSessions()

	session := &FollowJobSession{
		Id:       util.RandomToken(16),
		Alive:    true,
		Job:      job,
		EmitLogs: nil,
	}

	session.EmitLogs = func(rank int, stdout, stderr util.Option[string]) {
		resp := jobsProviderFollowResponse{
			StreamId: session.Id,
			Rank:     rank,
			Stdout:   stdout,
			Stderr:   stderr,
		}

		payload, err := json.Marshal(resp)
		if err != nil {
			session.Alive = false
			return
		}

		err = mainConnection.WriteJSON(WebSocketResponseMessage{
			Type:     "message",
			Payload:  payload,
			StreamId: wsStreamId,
		})

		if err != nil {
			session.Alive = false
		}
	}

	followSessionsMutex.Lock()
	followSessions[session.Id] = session
	followSessionsMutex.Unlock()

	return session
}

func cleanupFollowSessions() {
	followSessionsMutex.Lock()
	defer followSessionsMutex.Unlock()

	for key, session := range followSessions {
		if !session.Alive {
			delete(followSessions, key)
		}
	}
}

var followSessions = make(map[string]*FollowJobSession)
var followSessionsMutex = sync.Mutex{}
