package controller

import (
	"encoding/json"
	"fmt"
	"net/http"
	"slices"
	"strings"
	"sync"
	"unicode"

	ws "github.com/gorilla/websocket"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	gw "ucloud.dk/pkg/im/gateway"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var Jobs JobsService

type JobsService struct {
	Submit            func(request JobSubmitRequest) (util.Option[string], error)
	Terminate         func(request JobTerminateRequest) error
	Extend            func(request JobExtendRequest) error
	RetrieveProducts  func() []orc.JobSupport
	Follow            func(session *FollowJobSession)
	HandleShell       func(session *ShellSession, cols, rows int)
	ServerFindIngress func(job *orc.Job) ConfiguredWebIngress
	OpenWebSession    func(job *orc.Job, rank int) (cfg.HostInfo, error)
}

type ConfiguredWebIngress struct {
	IsPublic     bool
	TargetDomain string
}

type FollowJobSession struct {
	Id       string
	Alive    bool
	Job      *orc.Job
	EmitLogs func(rank int, stdout, stderr util.Option[string])
}

type ShellSession struct {
	Alive       bool
	Job         *orc.Job
	Rank        int
	InputEvents chan ShellEvent
	EmitData    func(data []byte)
}

type ShellEvent struct {
	Type ShellEventType
	ShellEventInput
	ShellEventResize
	ShellEventTerminate
}

type ShellEventInput struct {
	Data string
}

type ShellEventResize struct {
	Cols int
	Rows int
}

type ShellEventTerminate struct{}

type ShellEventType string

const (
	ShellEventTypeInit      ShellEventType = "initialize"
	ShellEventTypeInput     ShellEventType = "input"
	ShellEventTypeResize    ShellEventType = "resize"
	ShellEventTypeTerminate ShellEventType = "terminate"
)

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

type JobOpenInteractiveSessionRequest struct {
	Rank int
	Job  *orc.Job
	Type orc.InteractiveSessionType
}

func controllerJobs(mux *http.ServeMux) {
	if cfg.Mode == cfg.ServerModeServer {
		jobsIpcServer()
	}

	wsUpgrader := ws.Upgrader{
		ReadBufferSize:  1024 * 4,
		WriteBufferSize: 1024 * 4,
	}
	wsUpgrader.CheckOrigin = func(r *http.Request) bool { return true }
	jobContext := fmt.Sprintf("/ucloud/%v/jobs/", cfg.Provider.Id)

	if cfg.Mode == cfg.ServerModeUser {
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

		type openInteractiveSessionRequest struct {
			Job         *orc.Job                   `json:"job"`
			Rank        int                        `json:"rank"`
			SessionType orc.InteractiveSessionType `json:"sessionType"`
		}

		mux.HandleFunc(jobContext+"interactiveSession", HttpUpdateHandler[fnd.BulkRequest[openInteractiveSessionRequest]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[openInteractiveSessionRequest]) {
				var errors []error
				var responses []orc.OpenSession

				for _, item := range request.Items {
					switch item.SessionType {
					case orc.InteractiveSessionTypeShell:
						cleanupShellSessions()

						shellSessionsMutex.Lock()
						tok := util.RandomToken(32)
						shellSessions[tok] = &ShellSession{Alive: true, Job: item.Job, Rank: item.Rank}
						shellSessionsMutex.Unlock()
						responses = append(
							responses,
							orc.OpenSessionShell(item.Job.Id, item.Rank, tok, cfg.Provider.Hosts.SelfPublic.ToWebSocketUrl()),
						)

					case orc.InteractiveSessionTypeWeb:
						target, err := Jobs.OpenWebSession(item.Job, item.Rank)
						if err != nil {
							errors = append(errors, err)
						} else {
							redirect, err := RegisterWebIngress(item.Job, item.Rank, target)
							if err != nil {
								errors = append(errors, err)
							} else {
								responses = append(
									responses,
									orc.OpenSessionWeb(item.Job.Id, item.Rank, redirect, ""),
								)
							}
						}

					default:
						errors = append(errors, &util.HttpError{
							StatusCode: http.StatusBadRequest,
							Why:        "Not implemented",
						})
					}
				}

				if len(errors) > 0 {
					sendError(w, errors[0])
				} else {
					var response fnd.BulkResponse[orc.OpenSession]
					response.Responses = responses

					sendResponseOrError(w, response, nil)
				}
			}),
		)

		type shellRequest struct {
			Type              string `json:"type"`
			SessionIdentifier string `json:"sessionIdentifier,omitempty"`
			Cols              int    `json:"cols,omitempty"`
			Rows              int    `json:"rows,omitempty"`
			Data              string `json:"data,omitempty"`
		}
		mux.HandleFunc(
			fmt.Sprintf("/ucloud/%v/websocket", cfg.Provider.Id),
			func(writer http.ResponseWriter, request *http.Request) {
				conn, err := wsUpgrader.Upgrade(writer, request, nil)
				defer util.SilentCloseIfOk(conn, err)
				if err != nil {
					log.Debug("Expected a websocket connection, but couldn't upgrade: %v", err)
					return
				}

				var session *ShellSession = nil
				for util.IsAlive {
					if session != nil && !session.Alive {
						break
					}

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

					req := shellRequest{}
					err = json.Unmarshal(requestMessage.Payload, &req)
					if err != nil {
						log.Info("Failed to unmarshal follow message: %v", err)
						break
					}

					if session == nil {
						if req.Type == "initialize" {
							shellSessionsMutex.Lock()
							s, ok := shellSessions[req.SessionIdentifier]
							session = s
							shellSessionsMutex.Unlock()

							session.InputEvents = make(chan ShellEvent)
							session.EmitData = func(data []byte) {
								msg := map[string]string{
									"type": "data",
									"data": string(data),
								}
								asJson, _ := json.Marshal(msg)

								_ = conn.WriteJSON(WebSocketResponseMessage{
									Type:     "message",
									StreamId: requestMessage.StreamId,
									Payload:  asJson,
								})
							}

							go func() {
								Jobs.HandleShell(session, req.Cols, req.Rows)
								session.Alive = false
							}()

							_ = conn.WriteJSON(WebSocketResponseMessage{
								Type:     "message",
								StreamId: requestMessage.StreamId,
								Payload:  json.RawMessage(`{"type":"initialize"}`),
							})
							if !ok {
								break
							}
						}
						continue
					} else {
						switch req.Type {
						case "initialize":
							fallthrough
						case "resize":
							session.InputEvents <- ShellEvent{
								Type: ShellEventTypeResize,
								ShellEventResize: ShellEventResize{
									Cols: req.Cols,
									Rows: req.Rows,
								},
							}

						case "input":
							session.InputEvents <- ShellEvent{
								Type: ShellEventTypeInput,
								ShellEventInput: ShellEventInput{
									Data: req.Data,
								},
							}
						}

						_ = conn.WriteJSON(WebSocketResponseMessage{
							Type:     "message",
							StreamId: requestMessage.StreamId,
							Payload:  json.RawMessage(`{"type":"ack"}`),
						})
					}
				}
			},
		)

		followCall := fmt.Sprintf("jobs.provider.%v.follow", cfg.Provider.Id)
		mux.HandleFunc(
			fmt.Sprintf("/ucloud/jobs.provider.%v/websocket", cfg.Provider.Id),
			func(writer http.ResponseWriter, request *http.Request) {
				conn, err := HttpUpgradeToWebSocketAuthenticated(writer, request)
				defer util.SilentCloseIfOk(conn, err)
				if err != nil {
					log.Debug("Expected a websocket connection, but couldn't upgrade: %v", err)
					return
				}

				log.Info("We are now listening for logs (probably)")

				for util.IsAlive {
					messageType, data, err := conn.ReadMessage()
					log.Info("Entire message %v", string(data))

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
	} else if cfg.Mode == cfg.ServerModeServer {
		mux.HandleFunc(
			fmt.Sprintf("/ucloud/%v/authorize-app", cfg.Provider.Id),
			func(writer http.ResponseWriter, request *http.Request) {
				token := request.URL.Query().Get("token")
				webSessionsMutex.Lock()
				found := false
				for _, session := range webSessions {
					if slices.Contains(session.AuthToken, token) {
						authCookie := http.Cookie{
							Name:     "ucloud-compute-session-" + session.Ingress,
							Value:    token,
							Secure:   request.URL.Scheme == "https",
							HttpOnly: true,
							MaxAge:   1000 * 60 * 60 * 24 * 30,
							Path:     "/",
							Domain:   request.URL.Host,
						}
						http.SetCookie(writer, &authCookie)
						writer.Header().Set("Location", "/")
						writer.WriteHeader(http.StatusFound)
						found = true
						break
					}
				}
				webSessionsMutex.Unlock()

				if !found {
					writer.WriteHeader(http.StatusNotFound)
					_, _ = writer.Write([]byte("Unknown application requested. Please try again!"))
				}
			},
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
	}
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

func cleanupShellSessions() {
	shellSessionsMutex.Lock()
	defer shellSessionsMutex.Unlock()

	for key, session := range shellSessions {
		if !session.Alive {
			delete(shellSessions, key)
		}
	}
}

var shellSessions = make(map[string]*ShellSession)
var shellSessionsMutex = sync.Mutex{}

type jobRegisteredIngress struct {
	JobId  string
	Rank   int
	Target cfg.HostInfo
}

var jobsRegisterIngressCall = ipc.NewCall[jobRegisteredIngress, string]("ctrl.jobs.register_ingress")

type webSession struct {
	AuthToken []string
	Target    cfg.HostInfo
	Ingress   string
}

type jobIdAndRank struct {
	JobId string
	Rank  int
}

var webSessions = make(map[jobIdAndRank]webSession)
var webSessionsMutex = sync.Mutex{}

func jobsIpcServer() {
	jobsRegisterIngressCall.Handler(func(r *ipc.Request[jobRegisteredIngress]) ipc.Response[string] {
		job, ok := RetrieveJob(r.Payload.JobId)
		if !ok {
			return ipc.Response[string]{
				StatusCode: http.StatusNotFound,
				Payload:    "",
			}
		}

		if !BelongsToWorkspace(orc.ResourceOwnerToWalletOwner(job.Resource), r.Uid) {
			return ipc.Response[string]{
				StatusCode: http.StatusNotFound,
				Payload:    "",
			}
		}

		result, err := RegisterWebIngress(job, r.Payload.Rank, r.Payload.Target)
		if err != nil {
			return ipc.Response[string]{
				StatusCode: http.StatusInternalServerError,
				Payload:    "",
			}
		}

		return ipc.Response[string]{
			StatusCode: http.StatusOK,
			Payload:    result,
		}
	})
}

func RegisterWebIngress(job *orc.Job, rank int, target cfg.HostInfo) (string, error) {
	if cfg.Mode == cfg.ServerModeUser {
		return jobsRegisterIngressCall.Invoke(jobRegisteredIngress{
			JobId:  job.Id,
			Target: target,
		})
	} else {
		ingress := Jobs.ServerFindIngress(job)
		var authToken []string
		if !ingress.IsPublic {
			authToken = []string{util.RandomToken(12)}
		}

		{
			key := jobIdAndRank{
				JobId: job.Id,
				Rank:  rank,
			}
			webSessionsMutex.Lock()
			session, ok := webSessions[key]
			if ok {
				authToken = session.AuthToken
			}
			webSessions[key] = webSession{
				AuthToken: authToken,
				Target:    target,
				Ingress:   ingress.TargetDomain,
			}
			webSessionsMutex.Unlock()
		}

		gw.SendMessage(gw.ConfigurationMessage{
			ClusterUp: &gw.EnvoyCluster{
				Name:    "job_" + job.Id,
				Address: target.Address,
				Port:    target.Port,
				UseDNS:  !unicode.IsDigit([]rune(target.Address)[0]),
			},

			RouteUp: &gw.EnvoyRoute{
				Cluster:      "job_" + job.Id,
				CustomDomain: ingress.TargetDomain,
				AuthTokens:   authToken,
				Type:         gw.RouteTypeIngress,
			},
		})

		if ingress.IsPublic {
			return "https://" + ingress.TargetDomain, nil
		} else {
			return fmt.Sprintf("https://%v/ucloud/%v/authorize-app?token=%v", ingress.TargetDomain,
				cfg.Provider.Id, authToken[0]), nil
		}
	}
}
