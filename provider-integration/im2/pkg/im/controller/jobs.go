package controller

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"slices"
	"strings"
	"sync"
	"time"
	db "ucloud.dk/shared/pkg/database"
	"unicode"

	anyascii "github.com/anyascii/go"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"

	ws "github.com/gorilla/websocket"
	cfg "ucloud.dk/pkg/im/config"
	gw "ucloud.dk/pkg/im/gateway"
	"ucloud.dk/pkg/im/ipc"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var Jobs JobsService

type JobsService struct {
	Submit                   func(request JobSubmitRequest) (util.Option[string], error)
	Terminate                func(request JobTerminateRequest) error
	Suspend                  func(request JobSuspendRequest) error
	Unsuspend                func(request JobUnsuspendRequest) error
	Extend                   func(request JobExtendRequest) error
	RetrieveProducts         func() []orc.JobSupport
	Follow                   func(session *FollowJobSession)
	HandleShell              func(session *ShellSession, cols, rows int)
	ServerFindIngress        func(job *orc.Job, rank int, suffix util.Option[string]) ConfiguredWebIngress
	OpenWebSession           func(job *orc.Job, rank int, target util.Option[string]) (ConfiguredWebSession, error)
	RequestDynamicParameters func(owner orc.ResourceOwner, app *orc.Application) []orc.ApplicationParameter
	HandleBuiltInVnc         func(job *orc.Job, rank int, conn *ws.Conn)

	PublicIPs PublicIPService
	Ingresses IngressService
	Licenses  LicenseService
}

type PublicIPService struct {
	Create           func(ip *orc.PublicIp) error
	Delete           func(ip *orc.PublicIp) error
	RetrieveProducts func() []orc.PublicIpSupport
}

type LicenseService struct {
	Create           func(license *orc.License) error
	Delete           func(license *orc.License) error
	RetrieveProducts func() []orc.LicenseSupport
}

type IngressService struct {
	Create           func(ingress *orc.Ingress) error
	Delete           func(ingress *orc.Ingress) error
	RetrieveProducts func() []orc.IngressSupport
}

type ConfiguredWebSession struct {
	Host  cfg.HostInfo
	Flags RegisteredIngressFlags
}

type ConfiguredWebIngress struct {
	IsPublic     bool
	TargetDomain string
}

type FollowJobSession struct {
	Id       string
	Alive    *bool
	Job      *orc.Job
	EmitLogs func(rank int, stdout, stderr, channel util.Option[string])
}

type ShellSession struct {
	Alive          bool
	Folder         string
	Job            *orc.Job
	Rank           int
	InputEvents    chan ShellEvent
	EmitData       func(data []byte)
	UCloudUsername string
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
	Job       *orc.Job
	IsCleanup bool
}

type JobSuspendRequest struct {
	Job *orc.Job
}

type JobUnsuspendRequest struct {
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

var (
	metricJobsSubmitted = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "jobs",
		Name:      "submitted",
		Help:      "The total number of jobs submitted correctly to the UCloud/IM",
	})

	metricJobsInQueue = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud_im",
		Subsystem: "jobs",
		Name:      "in_queue",
		Help:      "The number of jobs currently in queue, registered by UCloud/IM",
	})

	metricJobsRunning = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud_im",
		Subsystem: "jobs",
		Name:      "running",
		Help:      "The number of jobs currently running, registered by UCloud/IM",
	})

	metricJobsSuspended = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud_im",
		Subsystem: "jobs",
		Name:      "suspended",
		Help:      "The number of jobs currently suspended, registered by UCloud/IM",
	})
)

func controllerJobs(mux *http.ServeMux) {
	if RunsServerCode() {
		jobsIpcServer()
	}

	wsUpgrader := ws.Upgrader{
		ReadBufferSize:  1024 * 4,
		WriteBufferSize: 1024 * 4,

		// The binary sub-protocol is needed to support VNC handled directly by the IM
		Subprotocols: []string{"binary"},
	}
	wsUpgrader.CheckOrigin = func(r *http.Request) bool { return true }
	jobContext := fmt.Sprintf("/ucloud/%v/jobs/", cfg.Provider.Id)
	publicIpContext := fmt.Sprintf("/ucloud/%v/networkips/", cfg.Provider.Id)
	licenseContext := fmt.Sprintf("/ucloud/%v/licenses/", cfg.Provider.Id)
	ingressContext := fmt.Sprintf("/ucloud/%v/ingresses/", cfg.Provider.Id)

	if RunsUserCode() {
		creationUrl, _ := strings.CutSuffix(jobContext, "/")
		mux.HandleFunc(creationUrl, HttpUpdateHandler[fnd.BulkRequest[*orc.Job]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[*orc.Job]) {
				var errors []error
				var providerIds []*fnd.FindByStringId

				for _, item := range request.Items {
					TrackNewJob(*item)

					providerGeneratedId, err := Jobs.Submit(JobSubmitRequest{
						JobToSubmit: item,
					})

					if providerGeneratedId.IsSet() && err == nil {
						providerIds = append(providerIds, &fnd.FindByStringId{Id: providerGeneratedId.Get()})
					} else {
						providerIds = append(providerIds, nil)
					}

					if err != nil {
						copied := *item
						copied.Status.State = orc.JobStateFailure
						TrackNewJob(copied)
						errors = append(errors, err)
					}
				}

				if len(errors) == 1 && len(request.Items) == 1 {
					sendError(w, errors[0])
				} else {
					metricJobsSubmitted.Inc()
					var response fnd.BulkResponse[*fnd.FindByStringId]
					response.Responses = providerIds
					sendResponseOrError(w, response, nil)
				}
			}),
		)

		type jobUpdateAclRequest struct {
			Resource orc.Job                `json:"resource"`
			Added    []orc.ResourceAclEntry `json:"added"`
			Deleted  []orc.AclEntity        `json:"deleted"`
		}

		mux.HandleFunc(jobContext+"updateAcl", HttpUpdateHandler[fnd.BulkRequest[jobUpdateAclRequest]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[jobUpdateAclRequest]) {
				resp := fnd.BulkResponse[util.Option[util.Empty]]{}

				for _, item := range request.Items {
					job := item.Resource

					for _, toDelete := range item.Deleted {
						for i, entry := range job.Permissions.Others {
							if entry.Entity == toDelete {
								slices.Delete(job.Permissions.Others, i, i+1)
							}
						}
					}

					for _, toAdd := range item.Added {
						found := false

						for i := 0; i < len(job.Permissions.Others); i++ {
							entry := &job.Permissions.Others[i]
							if entry.Entity == toAdd.Entity {
								for _, perm := range toAdd.Permissions {
									entry.Permissions = orc.PermissionsAdd(entry.Permissions, perm)
								}
								found = true
								break
							}
						}

						if !found {
							job.Permissions.Others = append(job.Permissions.Others, orc.ResourceAclEntry{
								Entity:      toAdd.Entity,
								Permissions: toAdd.Permissions,
							})
						}
					}

					TrackNewJob(job)

					resp.Responses = append(
						resp.Responses,
						util.Option[util.Empty]{
							Present: true,
						},
					)
				}

				sendResponseOrError(w, resp, nil)
			},
		))

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

		type terminateRequest = struct {
			Job *orc.Job `json:"job"`
		}
		mux.HandleFunc(jobContext+"suspend", HttpUpdateHandler[fnd.BulkRequest[terminateRequest]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[terminateRequest]) {
				var errors []error

				for _, item := range request.Items {
					err := Jobs.Suspend(JobSuspendRequest{
						Job: item.Job,
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

		mux.HandleFunc(jobContext+"unsuspend", HttpUpdateHandler[fnd.BulkRequest[terminateRequest]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[terminateRequest]) {
				var errors []error

				for _, item := range request.Items {
					err := Jobs.Unsuspend(JobUnsuspendRequest{
						Job: item.Job,
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
			Job           *orc.Job           `json:"job"`
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
			Target      util.Option[string]        `json:"target"`
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
						shellSessions[tok] = &ShellSession{Alive: true, Job: item.Job, Rank: item.Rank, UCloudUsername: GetUCloudUsername(r)}
						shellSessionsMutex.Unlock()
						responses = append(
							responses,
							orc.OpenSessionShell(item.Job.Id, item.Rank, tok, cfg.Provider.Hosts.SelfPublic.ToWebSocketUrl()),
						)

					case orc.InteractiveSessionTypeVnc:
						fallthrough
					case orc.InteractiveSessionTypeWeb:
						isVnc := item.SessionType == orc.InteractiveSessionTypeVnc
						var flags RegisteredIngressFlags

						target, err := Jobs.OpenWebSession(item.Job, item.Rank, item.Target)
						if err != nil {
							errors = append(errors, err)
						} else {
							flags = target.Flags
							if flags == 0 {
								if isVnc {
									flags |= RegisteredIngressFlagsVnc
								} else {
									flags |= RegisteredIngressFlagsWeb
								}
							}

							redirect, err := RegisterIngress(item.Job, item.Rank, target.Host, item.Target, flags)
							if err != nil {
								errors = append(errors, err)
							} else {
								if isVnc {
									password := item.Job.Status.ResolvedApplication.Invocation.Vnc.Password

									responses = append(
										responses,
										orc.OpenSessionVnc(item.Job.Id, item.Rank, redirect, password, ""),
									)
								} else {
									responses = append(
										responses,
										orc.OpenSessionWeb(item.Job.Id, item.Rank, redirect, ""),
									)
								}
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

		type openTerminalInFolder struct {
			Folder string `json:"folder"`
		}

		mux.HandleFunc(jobContext+"openTerminalInFolder", HttpUpdateHandler[fnd.BulkRequest[openTerminalInFolder]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[openTerminalInFolder]) {
				var errors []error
				var responses []orc.OpenSession

				for _, item := range request.Items {
					cleanupShellSessions()

					shellSessionsMutex.Lock()
					tok := util.RandomToken(32)
					shellSessions[tok] = &ShellSession{Alive: true, Folder: item.Folder, UCloudUsername: GetUCloudUsername(r)}
					shellSessionsMutex.Unlock()
					responses = append(
						responses,
						orc.OpenSessionShell(item.Folder, 0, tok, cfg.Provider.Hosts.SelfPublic.ToWebSocketUrl()),
					)
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
				if ok := checkEnvoySecret(writer, request); !ok {
					return
				}

				conn, err := wsUpgrader.Upgrade(writer, request, nil)
				defer util.SilentCloseIfOk(conn, err)
				if err != nil {
					log.Info("Expected a websocket connection, but couldn't upgrade: %v", err)
					return
				}

				connMutex := sync.Mutex{}

				var session *ShellSession = nil

				go func() {
					timeout := 30
					for util.IsAlive {
						if session != nil && !session.Alive {
							_ = conn.Close()
							break
						}

						if timeout <= 0 && session == nil {
							_ = conn.Close()
							break
						}

						time.Sleep(1 * time.Second)
						timeout--
					}
				}()

				for util.IsAlive {
					if session != nil && !session.Alive {
						break
					}

					messageType, data, err := conn.ReadMessage()
					if err != nil {
						break
					}

					if messageType != ws.TextMessage {
						log.Info("Only handling text messages but got a %v", messageType)
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
							if !ok || session == nil {
								log.Info("Bad session")
								break
							}

							session.InputEvents = make(chan ShellEvent)
							session.EmitData = func(data []byte) {
								if session.Alive {
									msg := map[string]string{
										"type": "data",
										"data": string(data),
									}
									asJson, _ := json.Marshal(msg)

									connMutex.Lock()
									_ = conn.WriteJSON(WebSocketResponseMessage{
										Type:     "message",
										StreamId: requestMessage.StreamId,
										Payload:  asJson,
									})
									connMutex.Unlock()
								}
							}

							go func() {
								Jobs.HandleShell(session, req.Cols, req.Rows)
								session.Alive = false
							}()

							connMutex.Lock()
							_ = conn.WriteJSON(WebSocketResponseMessage{
								Type:     "message",
								StreamId: requestMessage.StreamId,
								Payload:  json.RawMessage(`{"type":"initialize"}`),
							})
							connMutex.Unlock()
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

						connMutex.Lock()
						_ = conn.WriteJSON(WebSocketResponseMessage{
							Type:     "message",
							StreamId: requestMessage.StreamId,
							Payload:  json.RawMessage(`{"type":"ack"}`),
						})
						connMutex.Unlock()
					}
				}

				if session != nil {
					session.Alive = false
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

				connMutex := sync.Mutex{}
				sendMessage := func(message any) error {
					connMutex.Lock()
					err := conn.WriteJSON(message)
					connMutex.Unlock()
					return err
				}

				alive := true
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
						session := createFollowSession(requestMessage.StreamId, sendMessage, &alive, followRequest.Job)
						go func() {
							Jobs.Follow(session)
							*session.Alive = false

							dummy := jobsProviderFollowResponse{
								StreamId: session.Id,
								Rank:     0,
								Stdout:   util.Option[string]{},
								Stderr:   util.Option[string]{},
							}
							dummyData, _ := json.Marshal(dummy)
							_ = sendMessage(WebSocketResponseFin{
								Type:     "response",
								Status:   http.StatusOK,
								StreamId: requestMessage.StreamId,
								Payload:  dummyData,
							})
						}()

						go func() {
							for util.IsAlive && alive {
								_ = sendMessage(map[string]string{"ping": "pong"})
								time.Sleep(30 * time.Second)
							}
						}()

					case jobsProviderFollowRequestTypeCancel:
						followSessionsMutex.Lock()
						session, ok := followSessions[followRequest.StreamId]
						if ok {
							*session.Alive = false
							followSessionsMutex.Unlock()

							dummy := jobsProviderFollowResponse{
								StreamId: session.Id,
								Rank:     0,
								Stdout:   util.Option[string]{},
								Stderr:   util.Option[string]{},
							}
							dummyData, _ := json.Marshal(dummy)
							_ = sendMessage(WebSocketResponseFin{
								Type:     "response",
								Status:   http.StatusOK,
								StreamId: requestMessage.StreamId,
								Payload:  dummyData,
							})
						} else {
							followSessionsMutex.Unlock()
						}
					}
				}

				alive = false
			},
		)

		type dynamicParametersRequest struct {
			Owner       orc.ResourceOwner `json:"owner"`
			Application *orc.Application  `json:"application"`
		}
		type dynamicParametersResponse struct {
			Parameters []orc.ApplicationParameter `json:"parameters"`
		}
		mux.HandleFunc(
			jobContext+"requestDynamicParameters",
			HttpUpdateHandler[dynamicParametersRequest](0, func(w http.ResponseWriter, r *http.Request, request dynamicParametersRequest) {
				fn := Jobs.RequestDynamicParameters

				var resp []orc.ApplicationParameter
				if fn != nil {
					resp = fn(request.Owner, request.Application)
				}

				if resp == nil {
					resp = []orc.ApplicationParameter{}
				}

				sendResponseOrError(w, dynamicParametersResponse{Parameters: resp}, nil)
			}),
		)

		publicIpCreation, _ := strings.CutSuffix(publicIpContext, "/")
		publicIpCreateHandler := HttpUpdateHandler[fnd.BulkRequest[*orc.PublicIp]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[*orc.PublicIp]) {
				var errors []error
				var providerIds []*fnd.FindByStringId

				for _, item := range request.Items {
					TrackNewPublicIp(*item)
					providerIds = append(providerIds, nil)

					fn := Jobs.PublicIPs.Create
					if fn == nil {
						errors = append(errors, util.HttpErr(http.StatusBadRequest, "IP creation not supported"))
					} else {
						err := fn(item)
						if err != nil {
							errors = append(errors, err)
						}
					}
				}

				if len(errors) == 1 && len(request.Items) == 1 {
					sendError(w, errors[0])
				} else {
					var response fnd.BulkResponse[*fnd.FindByStringId]
					response.Responses = providerIds
					sendResponseOrError(w, response, nil)
				}
			},
		)

		publicIpDeleteHandler := HttpUpdateHandler[fnd.BulkRequest[*orc.PublicIp]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[*orc.PublicIp]) {
				var errors []error
				var resp []util.Option[util.Empty]

				for _, item := range request.Items {
					fn := Jobs.PublicIPs.Delete
					if fn == nil {
						errors = append(errors, util.HttpErr(http.StatusBadRequest, "IP deletion not supported"))
						resp = append(resp, util.Option[util.Empty]{Present: false})
					} else {
						err := fn(item)
						if err != nil {
							errors = append(errors, err)
							resp = append(resp, util.Option[util.Empty]{Present: false})
						} else {
							resp = append(resp, util.Option[util.Empty]{Present: true})
						}
					}
				}

				if len(errors) == 1 && len(request.Items) == 1 {
					sendError(w, errors[0])
				} else {
					var response fnd.BulkResponse[util.Option[util.Empty]]
					response.Responses = resp
					sendResponseOrError(w, response, nil)
				}
			},
		)
		mux.HandleFunc(publicIpCreation, func(w http.ResponseWriter, r *http.Request) {
			if r.Method == http.MethodPost {
				publicIpCreateHandler(w, r)
			} else if r.Method == http.MethodDelete {
				publicIpDeleteHandler(w, r)
			} else {
				sendResponseOrError(w, nil, util.HttpErr(http.StatusNotFound, "Not found"))
			}
		})

		mux.HandleFunc(publicIpContext+"firewall", HttpUpdateHandler[fnd.BulkRequest[orc.FirewallAndIp]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[orc.FirewallAndIp]) {
				var errors []error
				var resp []util.Option[util.Empty]

				for _, item := range request.Items {
					copied := item.Ip
					copied.Specification.Firewall = util.OptValue(item.Firewall)
					TrackNewPublicIp(copied)
					resp = append(resp, util.OptValue(util.EmptyValue))
				}

				if len(errors) == 1 && len(request.Items) == 1 {
					sendError(w, errors[0])
				} else {
					var response fnd.BulkResponse[util.Option[util.Empty]]
					response.Responses = resp
					sendResponseOrError(w, response, nil)
				}
			},
		))

		type publicIpUpdateAclRequest struct {
			Resource orc.PublicIp           `json:"resource"`
			Added    []orc.ResourceAclEntry `json:"added"`
			Deleted  []orc.AclEntity        `json:"deleted"`
		}

		mux.HandleFunc(publicIpContext+"updateAcl", HttpUpdateHandler[fnd.BulkRequest[publicIpUpdateAclRequest]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[publicIpUpdateAclRequest]) {
				resp := fnd.BulkResponse[util.Option[util.Empty]]{}

				for _, item := range request.Items {
					publicIp := item.Resource

					for _, toDelete := range item.Deleted {
						for i, entry := range publicIp.Permissions.Others {
							if entry.Entity == toDelete {
								slices.Delete(publicIp.Permissions.Others, i, i+1)
							}
						}
					}

					for _, toAdd := range item.Added {
						found := false

						for i := 0; i < len(publicIp.Permissions.Others); i++ {
							entry := &publicIp.Permissions.Others[i]
							if entry.Entity == toAdd.Entity {
								for _, perm := range toAdd.Permissions {
									entry.Permissions = orc.PermissionsAdd(entry.Permissions, perm)
								}
								found = true
								break
							}
						}

						if !found {
							publicIp.Permissions.Others = append(publicIp.Permissions.Others, orc.ResourceAclEntry{
								Entity:      toAdd.Entity,
								Permissions: toAdd.Permissions,
							})
						}
					}

					TrackNewPublicIp(publicIp)

					resp.Responses = append(
						resp.Responses,
						util.Option[util.Empty]{
							Present: true,
						},
					)
				}

				sendResponseOrError(w, resp, nil)
			},
		))

		ingressCreation, _ := strings.CutSuffix(ingressContext, "/")
		ingressCreateHandler := HttpUpdateHandler[fnd.BulkRequest[*orc.Ingress]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[*orc.Ingress]) {
				var errors []error
				var providerIds []*fnd.FindByStringId

				for _, item := range request.Items {
					TrackLink(*item)
					providerIds = append(providerIds, nil)

					fn := Jobs.Ingresses.Create
					if fn == nil {
						errors = append(errors, util.HttpErr(http.StatusBadRequest, "Public link creation not supported"))
					} else {
						err := fn(item)
						if err != nil {
							errors = append(errors, err)
						}
					}
				}

				if len(errors) == 1 && len(request.Items) == 1 {
					sendError(w, errors[0])
				} else {
					var response fnd.BulkResponse[*fnd.FindByStringId]
					response.Responses = providerIds
					sendResponseOrError(w, response, nil)
				}
			},
		)

		ingressDeleteHandler := HttpUpdateHandler[fnd.BulkRequest[*orc.Ingress]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[*orc.Ingress]) {
				var errors []error
				var resp []util.Option[util.Empty]

				for _, item := range request.Items {
					fn := Jobs.Ingresses.Delete
					if fn == nil {
						errors = append(errors, util.HttpErr(http.StatusBadRequest, "Public link deletion not supported"))
						resp = append(resp, util.Option[util.Empty]{Present: false})
					} else {
						err := fn(item)
						if err != nil {
							errors = append(errors, err)
							resp = append(resp, util.Option[util.Empty]{Present: false})
						} else {
							resp = append(resp, util.Option[util.Empty]{Present: true})
						}
					}
				}

				if len(errors) == 1 && len(request.Items) == 1 {
					sendError(w, errors[0])
				} else {
					var response fnd.BulkResponse[util.Option[util.Empty]]
					response.Responses = resp
					sendResponseOrError(w, response, nil)
				}
			},
		)

		mux.HandleFunc(ingressCreation, func(w http.ResponseWriter, r *http.Request) {
			if r.Method == http.MethodPost {
				ingressCreateHandler(w, r)
			} else if r.Method == http.MethodDelete {
				ingressDeleteHandler(w, r)
			} else {
				sendResponseOrError(w, nil, util.HttpErr(http.StatusNotFound, "Not found"))
			}
		})

		type ingressUpdateAclRequest struct {
			Resource orc.Ingress            `json:"resource"`
			Added    []orc.ResourceAclEntry `json:"added"`
			Deleted  []orc.AclEntity        `json:"deleted"`
		}

		mux.HandleFunc(ingressContext+"updateAcl", HttpUpdateHandler[fnd.BulkRequest[ingressUpdateAclRequest]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[ingressUpdateAclRequest]) {
				resp := fnd.BulkResponse[util.Option[util.Empty]]{}

				for _, item := range request.Items {
					ingress := item.Resource

					for _, toDelete := range item.Deleted {
						for i, entry := range ingress.Permissions.Others {
							if entry.Entity == toDelete {
								slices.Delete(ingress.Permissions.Others, i, i+1)
							}
						}
					}

					for _, toAdd := range item.Added {
						found := false

						for i := 0; i < len(ingress.Permissions.Others); i++ {
							entry := &ingress.Permissions.Others[i]
							if entry.Entity == toAdd.Entity {
								for _, perm := range toAdd.Permissions {
									entry.Permissions = orc.PermissionsAdd(entry.Permissions, perm)
								}
								found = true
								break
							}
						}

						if !found {
							ingress.Permissions.Others = append(ingress.Permissions.Others, orc.ResourceAclEntry{
								Entity:      toAdd.Entity,
								Permissions: toAdd.Permissions,
							})
						}
					}

					TrackLink(ingress)

					resp.Responses = append(
						resp.Responses,
						util.Option[util.Empty]{
							Present: true,
						},
					)
				}

				sendResponseOrError(w, resp, nil)
			},
		))

		licenseActivation, _ := strings.CutSuffix(licenseContext, "/")
		licenseActivateHandler := HttpUpdateHandler[fnd.BulkRequest[*orc.License]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[*orc.License]) {
				var errors []error
				var providerIds []*fnd.FindByStringId

				for _, item := range request.Items {
					TrackLicense(*item)
					providerIds = append(providerIds, nil)

					fn := Jobs.Licenses.Create
					if fn == nil {
						errors = append(errors, util.HttpErr(http.StatusBadRequest, "License activation not supported"))
					} else {
						err := fn(item)
						if err != nil {
							errors = append(errors, err)
						}
					}
				}

				if len(errors) == 1 && len(request.Items) == 1 {
					sendError(w, errors[0])
				} else {
					var response fnd.BulkResponse[*fnd.FindByStringId]
					response.Responses = providerIds
					sendResponseOrError(w, response, nil)
				}
			},
		)

		licenseDeleteHandler := HttpUpdateHandler[fnd.BulkRequest[*orc.License]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[*orc.License]) {
				var errors []error
				var resp []util.Option[util.Empty]

				for _, item := range request.Items {
					fn := Jobs.Licenses.Delete
					if fn == nil {
						errors = append(errors, util.HttpErr(http.StatusBadRequest, "License deletion not supported"))
						resp = append(resp, util.Option[util.Empty]{Present: false})
					} else {
						err := fn(item)
						if err != nil {
							errors = append(errors, err)
							resp = append(resp, util.Option[util.Empty]{Present: false})
						} else {
							resp = append(resp, util.Option[util.Empty]{Present: true})
						}
					}
				}

				if len(errors) == 1 && len(request.Items) == 1 {
					sendError(w, errors[0])
				} else {
					var response fnd.BulkResponse[util.Option[util.Empty]]
					response.Responses = resp
					sendResponseOrError(w, response, nil)
				}
			},
		)

		mux.HandleFunc(licenseActivation, func(w http.ResponseWriter, r *http.Request) {
			if r.Method == http.MethodPost {
				licenseActivateHandler(w, r)
			} else if r.Method == http.MethodDelete {
				licenseDeleteHandler(w, r)
			} else {
				sendResponseOrError(w, nil, util.HttpErr(http.StatusNotFound, "Not found"))
			}
		})

		type licenseUpdateAclRequest struct {
			Resource orc.License            `json:"resource"`
			Added    []orc.ResourceAclEntry `json:"added"`
			Deleted  []orc.AclEntity        `json:"deleted"`
		}

		mux.HandleFunc(licenseContext+"updateAcl", HttpUpdateHandler[fnd.BulkRequest[licenseUpdateAclRequest]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[licenseUpdateAclRequest]) {
				resp := fnd.BulkResponse[util.Option[util.Empty]]{}

				for _, item := range request.Items {
					license := item.Resource

					for _, toDelete := range item.Deleted {
						for i, entry := range license.Permissions.Others {
							if entry.Entity == toDelete {
								slices.Delete(license.Permissions.Others, i, i+1)
							}
						}
					}

					for _, toAdd := range item.Added {
						found := false

						for i := 0; i < len(license.Permissions.Others); i++ {
							entry := &license.Permissions.Others[i]
							if entry.Entity == toAdd.Entity {
								for _, perm := range toAdd.Permissions {
									entry.Permissions = orc.PermissionsAdd(entry.Permissions, perm)
								}
								found = true
								break
							}
						}

						if !found {
							license.Permissions.Others = append(license.Permissions.Others, orc.ResourceAclEntry{
								Entity:      toAdd.Entity,
								Permissions: toAdd.Permissions,
							})
						}
					}

					TrackLicense(license)

					resp.Responses = append(
						resp.Responses,
						util.Option[util.Empty]{
							Present: true,
						},
					)
				}

				sendResponseOrError(w, resp, nil)
			},
		))

	}

	if RunsServerCode() {
		mux.HandleFunc(
			fmt.Sprintf("/ucloud/%v/authorize-app", cfg.Provider.Id),
			func(writer http.ResponseWriter, request *http.Request) {
				token := request.URL.Query().Get("token")
				webSessionsMutex.RLock()
				found := false
				for _, session := range webSessions {
					for _, ing := range session.IngressBySuffix {
						if ing.AuthToken.Present && ing.AuthToken.Value == token {
							authCookie := http.Cookie{
								Name:     fmt.Sprintf("ucloud-compute-session-%v-%v-%v", ing.JobId, ing.Rank, ing.Suffix),
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
				}
				webSessionsMutex.RUnlock()

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

		mux.HandleFunc(publicIpContext+"retrieveProducts", HttpRetrieveHandler[util.Empty](
			0,
			func(w http.ResponseWriter, r *http.Request, _ util.Empty) {
				var result []orc.PublicIpSupport
				fn := Jobs.PublicIPs.RetrieveProducts
				if fn != nil {
					result = fn()
				}

				sendResponseOrError(
					w,
					fnd.BulkResponse[orc.PublicIpSupport]{
						Responses: result,
					},
					nil,
				)
			}),
		)

		mux.HandleFunc(ingressContext+"retrieveProducts", HttpRetrieveHandler[util.Empty](
			0,
			func(w http.ResponseWriter, r *http.Request, _ util.Empty) {
				var result []orc.IngressSupport
				fn := Jobs.Ingresses.RetrieveProducts
				if fn != nil {
					result = fn()
				}

				log.Info("retrieve ingress products called. Returning %s", result)

				sendResponseOrError(
					w,
					fnd.BulkResponse[orc.IngressSupport]{
						Responses: result,
					},
					nil,
				)
			}),
		)

		mux.HandleFunc(licenseContext+"retrieveProducts", HttpRetrieveHandler[util.Empty](
			0,
			func(w http.ResponseWriter, r *http.Request, _ util.Empty) {
				var result []orc.LicenseSupport
				fn := Jobs.Licenses.RetrieveProducts
				if fn != nil {
					result = fn()
				}

				sendResponseOrError(
					w,
					fnd.BulkResponse[orc.LicenseSupport]{
						Responses: result,
					},
					nil,
				)
			}),
		)

		mux.HandleFunc(
			fmt.Sprintf("/ucloud/%v/vnc", cfg.Provider.Id),
			func(writer http.ResponseWriter, request *http.Request) {
				if ok := checkEnvoySecret(writer, request); !ok {
					return
				}

				var idAndRank jobIdAndRank
				token := request.URL.Query().Get("token")
				webSessionsMutex.RLock()
				for key, session := range webSessions {
					for _, ing := range session.IngressBySuffix {
						if ing.AuthToken.Present && ing.AuthToken.Value == token {
							idAndRank = key
							break
						}
					}
				}
				webSessionsMutex.RUnlock()

				if idAndRank.JobId == "" {
					sendError(writer, &util.HttpError{
						StatusCode: http.StatusForbidden,
						Why:        "Forbidden",
					})
					return
				}

				job, ok := RetrieveJob(idAndRank.JobId)
				if !ok {
					sendError(writer, &util.HttpError{
						StatusCode: http.StatusInternalServerError,
						Why:        "Unknown job",
					})
					return
				}

				handler := Jobs.HandleBuiltInVnc
				if handler == nil {
					sendError(writer, &util.HttpError{
						StatusCode: http.StatusInternalServerError,
						Why:        "not supported",
					})
					return
				}

				conn, err := wsUpgrader.Upgrade(writer, request, nil)
				defer util.SilentCloseIfOk(conn, err)
				if err != nil {
					log.Info("Expected a websocket connection, but couldn't upgrade: %v", err)
					return
				}

				handler(job, idAndRank.Rank, conn)
			},
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
	Channel  util.Option[string] `json:"channel"`
}

func createFollowSession(
	wsStreamId string,
	writeMessage func(message any) error,
	alive *bool,
	job *orc.Job,
) *FollowJobSession {
	cleanupFollowSessions()

	session := &FollowJobSession{
		Id:       util.RandomToken(16),
		Alive:    alive,
		Job:      job,
		EmitLogs: nil,
	}

	session.EmitLogs = func(rank int, stdout, stderr, channel util.Option[string]) {
		resp := jobsProviderFollowResponse{
			StreamId: session.Id,
			Rank:     rank,
			Stdout:   stdout,
			Stderr:   stderr,
			Channel:  channel,
		}

		payload, err := json.Marshal(resp)
		if err != nil {
			*session.Alive = false
			return
		}

		err = writeMessage(WebSocketResponseMessage{
			Type:     "message",
			Payload:  payload,
			StreamId: wsStreamId,
		})

		if err != nil {
			*session.Alive = false
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
		if !*session.Alive {
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
	JobId           string
	Rank            int
	Target          cfg.HostInfo
	RequestedSuffix util.Option[string]
	Flags           RegisteredIngressFlags
}

var jobsRegisterIngressCall = ipc.NewCall[jobRegisteredIngress, string]("ctrl.jobs.register_ingress")

type webSession struct {
	JobId              string
	Rank               int
	IngressBySuffix    map[string]webSessionIngress
	IngressInitialized map[string]util.Empty
}

type webSessionIngress struct {
	JobId     string
	Rank      int
	Target    cfg.HostInfo
	Suffix    string
	Flags     RegisteredIngressFlags
	AuthToken util.Option[string]
	Address   string
}

type jobIdAndRank struct {
	JobId string
	Rank  int
}

var webSessions = make(map[jobIdAndRank]*webSession)
var webSessionsMutex = sync.RWMutex{}

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

		result, err := RegisterIngress(job, r.Payload.Rank, r.Payload.Target, r.Payload.RequestedSuffix, r.Payload.Flags)
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

type RegisteredIngressFlags int

const (
	RegisteredIngressFlagsWeb RegisteredIngressFlags = 1 << iota
	RegisteredIngressFlagsVnc
	RegisteredIngressFlagsNoGatewayConfig
	RegisteredIngressFlagsNoPersist
)

// ToHostnameSafe transforms a string into a hostname-safe version
func ToHostnameSafe(input string) string {
	var builder strings.Builder
	lastWasHyphen := false

	// Convert to ASCII-equivalent where possible
	input = anyascii.Transliterate(input)

	// Normalize by lowercasing everything and trimming spaces.
	input = strings.ToLower(input)
	input = strings.TrimSpace(input)

	for _, r := range input {
		if r > unicode.MaxASCII {
			continue
		} else if unicode.IsSpace(r) {
			if !lastWasHyphen {
				builder.WriteRune('-') // Replace space with a single hyphen
				lastWasHyphen = true
			}
		} else if unicode.IsLetter(r) || unicode.IsDigit(r) || r == '-' {
			builder.WriteRune(r) // Keep alphanumeric and dash
			lastWasHyphen = false
		}
	}

	result := builder.String()

	// Trim trailing hyphen if present
	if lastWasHyphen {
		if len(result) > 0 && result[len(result)-1] == '-' {
			result = result[:len(result)-1]
		}
	}

	return result
}

func RegisterIngress(job *orc.Job, rank int, target cfg.HostInfo, requestedSuffix util.Option[string], flags RegisteredIngressFlags) (string, error) {
	isWeb := (flags & RegisteredIngressFlagsWeb) != 0
	isVnc := (flags & RegisteredIngressFlagsVnc) != 0

	if !isWeb && !isVnc {
		return "", fmt.Errorf("must specify either RegisteredIngressFlagsWeb or RegisteredIngressFlagsVnc")
	}

	if !RunsServerCode() {
		return jobsRegisterIngressCall.Invoke(jobRegisteredIngress{
			JobId:           job.Id,
			Target:          target,
			RequestedSuffix: requestedSuffix,
			Flags:           flags,
		})
	} else {
		suffix := ""
		if requestedSuffix.Present {
			suffix = "-" + ToHostnameSafe(requestedSuffix.Value)
		}

		var ingress webSessionIngress

		webSessionsMutex.RLock()
		key := jobIdAndRank{
			JobId: job.Id,
			Rank:  rank,
		}
		needInit := false
		session, ok := webSessions[key]
		if ok {
			ingress, ok = session.IngressBySuffix[suffix]
			needInit = !ok
		} else {
			needInit = true
		}
		webSessionsMutex.RUnlock()

		if needInit {
			var ingressConfig ConfiguredWebIngress

			if isWeb {
				ingressConfig = Jobs.ServerFindIngress(job, rank, util.Option[string]{Present: requestedSuffix.Present, Value: suffix})
			} else {
				ingressConfig = ConfiguredWebIngress{
					IsPublic:     false,
					TargetDomain: cfg.Provider.Hosts.SelfPublic.Address,
				}
			}

			webSessionsMutex.Lock()
			session, ok = webSessions[key]
			if !ok {
				session = &webSession{
					JobId:              job.Id,
					Rank:               rank,
					IngressBySuffix:    make(map[string]webSessionIngress),
					IngressInitialized: make(map[string]util.Empty),
				}

				webSessions[key] = session
			}
			ingress, ok = session.IngressBySuffix[suffix]
			if !ok {
				token := util.Option[string]{}
				if !ingressConfig.IsPublic {
					token.Set(util.RandomToken(12))
				}

				ingress = webSessionIngress{
					JobId:     job.Id,
					Rank:      rank,
					Target:    target,
					Suffix:    suffix,
					Flags:     flags,
					AuthToken: token,
					Address:   ingressConfig.TargetDomain,
				}

				session.IngressBySuffix[suffix] = ingress

				if flags&RegisteredIngressFlagsNoPersist == 0 {
					db.NewTx0(func(tx *db.Transaction) {
						sqlSuffix := sql.NullString{}
						if suffix != "" {
							sqlSuffix.Valid = true
							sqlSuffix.String = suffix
						}

						sqlToken := sql.NullString{}
						if token.Present {
							sqlToken.Valid = true
							sqlToken.String = token.Value
						}

						db.Exec(
							tx,
							`
								insert into web_sessions(job_id, rank, target_address, target_port, address, suffix, 
									auth_token, flags) 
								values (:job_id, :rank, :target_address, :target_port, :address, :suffix, 
									:auth_token, :flags)
							`,
							db.Params{
								"job_id":         job.Id,
								"rank":           rank,
								"target_address": target.Address,
								"target_port":    target.Port,
								"suffix":         sqlSuffix,
								"auth_token":     sqlToken,
								"flags":          flags,
								"address":        ingressConfig.TargetDomain,
							},
						)
					})
				}
			}
			webSessionsMutex.Unlock()
			refreshJobRoutes()
		}

		if isWeb {
			if !ingress.AuthToken.Present {
				return "https://" + ingress.Address, nil
			} else {
				return fmt.Sprintf("https://%v/ucloud/%v/authorize-app?token=%v", ingress.Address,
					cfg.Provider.Id, ingress.AuthToken.Value), nil
			}
		} else if isVnc {
			return fmt.Sprintf("https://%v/ucloud/%v/vnc?token=%v", ingress.Address,
				cfg.Provider.Id, ingress.AuthToken.Value), nil
		} else {
			return "", fmt.Errorf("unhandled case %v", flags)
		}
	}
}

func jobsLoadSessions() {
	webSessionsMutex.Lock()
	db.NewTx0(func(tx *db.Transaction) {
		rows := db.Select[struct {
			JobId         string
			Rank          int
			TargetAddress string
			TargetPort    int
			Suffix        sql.NullString
			AuthToken     sql.NullString
			Flags         int
			Address       string
		}](
			tx,
			`
				select job_id, rank, target_address, target_port, address, suffix, auth_token, flags
				from web_sessions
		    `,
			db.Params{},
		)

		for _, row := range rows {
			key := jobIdAndRank{
				JobId: row.JobId,
				Rank:  row.Rank,
			}

			session, ok := webSessions[key]
			if !ok {
				session = &webSession{
					JobId:              row.JobId,
					Rank:               row.Rank,
					IngressBySuffix:    make(map[string]webSessionIngress),
					IngressInitialized: make(map[string]util.Empty),
				}

				webSessions[key] = session
			}

			tok := util.Option[string]{}
			if row.AuthToken.Valid {
				tok.Set(row.AuthToken.String)
			}

			session.IngressBySuffix[row.Suffix.String] = webSessionIngress{
				JobId: row.JobId,
				Rank:  row.Rank,
				Target: cfg.HostInfo{
					Address: row.TargetAddress,
					Port:    row.TargetPort,
				},
				Suffix:    row.Suffix.String,
				Flags:     RegisteredIngressFlags(row.Flags),
				AuthToken: tok,
				Address:   row.Address,
			}
		}
	})
	webSessionsMutex.Unlock()

	refreshJobRoutes()
}

func refreshJobRoutes() {
	allJobs := JobsListServer()
	allJobsById := map[string]*orc.Job{}
	for _, job := range allJobs {
		allJobsById[job.Id] = job
	}

	getClusterName := func(session *webSession, suffix string) string {
		return "job_" + session.JobId + "_" + fmt.Sprint(session.Rank) + suffix
	}

	webSessionsMutex.Lock()
	var sessionsToDelete []jobIdAndRank
	for key, session := range webSessions {
		if _, ok := allJobsById[key.JobId]; !ok {
			sessionsToDelete = append(sessionsToDelete, key)
		} else {
			for suffix, ingress := range session.IngressBySuffix {
				if _, didInit := session.IngressInitialized[suffix]; !didInit {
					session.IngressInitialized[suffix] = util.Empty{}

					flags := ingress.Flags
					isVnc := (flags & RegisteredIngressFlagsVnc) != 0

					if flags&RegisteredIngressFlagsNoGatewayConfig == 0 {
						routeType := gw.RouteTypeIngress
						if isVnc {
							routeType = gw.RouteTypeVnc
						}

						var tokens []string
						if ingress.AuthToken.Present {
							tokens = []string{ingress.AuthToken.Value}
						}

						clusterName := getClusterName(session, suffix)

						gw.SendMessage(gw.ConfigurationMessage{
							ClusterUp: &gw.EnvoyCluster{
								Name:    clusterName,
								Address: ingress.Target.Address,
								Port:    ingress.Target.Port,
								UseDNS:  !unicode.IsDigit([]rune(ingress.Target.Address)[0]),
							},

							RouteUp: &gw.EnvoyRoute{
								Cluster:      clusterName,
								CustomDomain: ingress.Address,
								AuthTokens:   tokens,
								Type:         routeType,
							},
						})
					}
				}
			}
		}
	}

	var jobIds []string
	for _, toDeleteKey := range sessionsToDelete {
		jobIds = append(jobIds, toDeleteKey.JobId)
		session := webSessions[toDeleteKey]
		delete(webSessions, toDeleteKey)

		for suffix := range session.IngressBySuffix {
			if _, didInit := session.IngressInitialized[suffix]; didInit {
				clusterName := getClusterName(session, suffix)

				// NOTE(Dan): Routes are automatically deleted by the gateway, we only need to take down the cluster.
				gw.SendMessage(gw.ConfigurationMessage{
					ClusterDown: &gw.EnvoyCluster{
						Name: clusterName,
					},
				})
			}
		}
	}

	if len(jobIds) > 0 {
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`delete from web_sessions where job_id = some(:job_ids)`,
				db.Params{"job_ids": jobIds},
			)
		})
	}

	webSessionsMutex.Unlock()
}
