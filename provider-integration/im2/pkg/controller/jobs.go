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
	"unicode"

	cfg "ucloud.dk/pkg/config"
	gw "ucloud.dk/pkg/gateway"
	"ucloud.dk/pkg/ipc"
	db "ucloud.dk/shared/pkg/database"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"

	anyascii "github.com/anyascii/go"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"

	ws "github.com/gorilla/websocket"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

var Jobs JobsService

type JobsService struct {
	Submit                   func(request orcapi.Job) (util.Option[string], *util.HttpError)
	Terminate                func(request JobTerminateRequest) *util.HttpError
	Suspend                  func(request orcapi.Job) *util.HttpError
	Unsuspend                func(request orcapi.Job) *util.HttpError
	Extend                   func(request orcapi.JobsProviderExtendRequestItem) *util.HttpError
	RetrieveProducts         func() []orcapi.JobSupport
	Follow                   func(session *FollowJobSession)
	HandleShell              func(session *ShellSession, cols, rows int)
	ServerFindIngress        func(job *orcapi.Job, rank int, suffix util.Option[string]) ConfiguredWebIngress
	OpenWebSession           func(job *orcapi.Job, rank int, target util.Option[string]) (ConfiguredWebSession, *util.HttpError)
	RequestDynamicParameters func(owner orcapi.ResourceOwner, app *orcapi.Application) []orcapi.ApplicationParameter
	HandleBuiltInVnc         func(job *orcapi.Job, rank int, conn *ws.Conn)

	PublicIPs       PublicIPService
	Ingresses       IngressService
	Licenses        LicenseService
	PrivateNetworks PrivateNetworkService
}

type PublicIPService struct {
	Create           func(ip *orcapi.PublicIp) *util.HttpError
	Delete           func(ip *orcapi.PublicIp) *util.HttpError
	RetrieveProducts func() []orcapi.PublicIpSupport
}

type LicenseService struct {
	Create           func(license *orcapi.License) *util.HttpError
	Delete           func(license *orcapi.License) *util.HttpError
	RetrieveProducts func() []orcapi.LicenseSupport
}

type IngressService struct {
	Create           func(ingress *orcapi.Ingress) *util.HttpError
	Delete           func(ingress *orcapi.Ingress) *util.HttpError
	RetrieveProducts func() []orcapi.IngressSupport
}

type PrivateNetworkService struct {
	Create           func(network *orcapi.PrivateNetwork) *util.HttpError
	Delete           func(network *orcapi.PrivateNetwork) *util.HttpError
	RetrieveProducts func() []orcapi.PrivateNetworkSupport
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
	Job      *orcapi.Job
	EmitLogs func(rank int, stdout, stderr, channel util.Option[string])
}

type ShellSession struct {
	Alive          bool
	Folder         string
	Job            *orcapi.Job
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
	Job       *orcapi.Job
	IsCleanup bool
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

func initJobs() {
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

	if RunsUserCode() {
		orcapi.JobsProviderCreate.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.Job]) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
			var errors []*util.HttpError
			var providerIds []fnd.FindByStringId

			for _, item := range request.Items {
				if item.Specification.Application.Name == "unknown" {
					errors = append(errors, util.HttpErr(http.StatusBadRequest, "Invalid application specified"))
					continue
				}

				JobTrackNew(item)

				providerGeneratedId, err := Jobs.Submit(item)

				if providerGeneratedId.IsSet() && err == nil {
					providerIds = append(providerIds, fnd.FindByStringId{Id: providerGeneratedId.Get()})
				} else {
					providerIds = append(providerIds, fnd.FindByStringId{})
				}

				if err != nil {
					copied := item
					copied.Status.State = orcapi.JobStateFailure
					JobTrackNew(copied)
					errors = append(errors, err)
				}
			}

			if len(errors) == 1 && len(request.Items) == 1 {
				return fnd.BulkResponse[fnd.FindByStringId]{}, errors[0]
			} else {
				metricJobsSubmitted.Inc()
				var response fnd.BulkResponse[fnd.FindByStringId]
				response.Responses = providerIds
				return response, nil
			}
		})

		orcapi.JobsProviderUpdateAcl.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.UpdatedAclWithResource[orcapi.Job]]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			resp := fnd.BulkResponse[util.Empty]{}

			for _, item := range request.Items {
				job := item.Resource

				permissions := job.Permissions.Value
				for _, toDelete := range item.Deleted {
					for i, entry := range permissions.Others {
						if entry.Entity == toDelete {
							slices.Delete(permissions.Others, i, i+1)
						}
					}
				}

				for _, toAdd := range item.Added {
					found := false

					for i := 0; i < len(permissions.Others); i++ {
						entry := &permissions.Others[i]
						if entry.Entity == toAdd.Entity {
							for _, perm := range toAdd.Permissions {
								entry.Permissions = orcapi.PermissionsAdd(entry.Permissions, perm)
							}
							found = true
							break
						}
					}

					if !found {
						permissions.Others = append(permissions.Others, orcapi.ResourceAclEntry{
							Entity:      toAdd.Entity,
							Permissions: toAdd.Permissions,
						})
					}
				}

				JobTrackNew(job)

				resp.Responses = append(resp.Responses, util.Empty{})
			}

			return resp, nil
		})

		orcapi.JobsProviderTerminate.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.Job]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var errors []*util.HttpError

			for _, item := range request.Items {
				err := Jobs.Terminate(JobTerminateRequest{Job: &item})

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				return fnd.BulkResponse[util.Empty]{}, errors[0]
			} else {
				var response fnd.BulkResponse[util.Empty]
				for i := 0; i < len(request.Items); i++ {
					response.Responses = append(response.Responses, util.Empty{})
				}

				return response, nil
			}
		})

		orcapi.JobsProviderSuspend.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.JobsProviderSuspendRequestItem]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var errors []*util.HttpError

			for _, item := range request.Items {
				err := Jobs.Suspend(item.Job)

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				return fnd.BulkResponse[util.Empty]{}, errors[0]
			} else {
				var response fnd.BulkResponse[util.Empty]
				for i := 0; i < len(request.Items); i++ {
					response.Responses = append(response.Responses, util.Empty{})
				}

				return response, nil
			}
		})

		orcapi.JobsProviderUnsuspend.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.JobsProviderUnsuspendRequestItem]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var errors []*util.HttpError

			for _, item := range request.Items {
				err := Jobs.Unsuspend(item.Job)

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				return fnd.BulkResponse[util.Empty]{}, errors[0]
			} else {
				var response fnd.BulkResponse[util.Empty]
				for i := 0; i < len(request.Items); i++ {
					response.Responses = append(response.Responses, util.Empty{})
				}

				return response, nil
			}
		})

		orcapi.JobsProviderExtend.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.JobsProviderExtendRequestItem]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var errors []*util.HttpError

			for _, item := range request.Items {
				err := Jobs.Extend(item)

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				return fnd.BulkResponse[util.Empty]{}, errors[0]
			} else {
				var response fnd.BulkResponse[util.Empty]
				for i := 0; i < len(request.Items); i++ {
					response.Responses = append(response.Responses, util.Empty{})
				}

				return response, nil
			}
		})

		orcapi.JobsProviderOpenInteractiveSession.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.JobsProviderOpenInteractiveSessionRequestItem]) (fnd.BulkResponse[orcapi.OpenSession], *util.HttpError) {
			var errors []*util.HttpError
			var responses []orcapi.OpenSession

			for _, item := range request.Items {
				switch item.SessionType {
				case orcapi.InteractiveSessionTypeShell:
					jobCleanupShellSessions()

					shellSessionsMutex.Lock()
					tok := util.RandomToken(32)
					shellSessions[tok] = &ShellSession{Alive: true, Job: &item.Job, Rank: item.Rank, UCloudUsername: info.Actor.Username}
					shellSessionsMutex.Unlock()
					responses = append(
						responses,
						orcapi.OpenSessionShell(item.Job.Id, item.Rank, tok, cfg.Provider.Hosts.SelfPublic.ToWebSocketUrl()),
					)

				case orcapi.InteractiveSessionTypeVnc:
					fallthrough
				case orcapi.InteractiveSessionTypeWeb:
					isVnc := item.SessionType == orcapi.InteractiveSessionTypeVnc
					var flags RegisteredIngressFlags

					target, err := Jobs.OpenWebSession(&item.Job, item.Rank, item.Target)
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

						redirect, err := IngressRegisterWithJob(&item.Job, item.Rank, target.Host, item.Target, flags)
						if err != nil {
							errors = append(errors, err)
						} else {
							if isVnc {
								password := item.Job.Status.ResolvedApplication.Value.Invocation.Vnc.Value.Password

								responses = append(
									responses,
									orcapi.OpenSessionVnc(item.Job.Id, item.Rank, redirect, password, ""),
								)
							} else {
								responses = append(
									responses,
									orcapi.OpenSessionWeb(item.Job.Id, item.Rank, redirect, ""),
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
				return fnd.BulkResponse[orcapi.OpenSession]{}, errors[0]
			} else {
				var response fnd.BulkResponse[orcapi.OpenSession]
				response.Responses = responses

				return response, nil
			}
		})

		orcapi.JobsProviderOpenTerminalInFolder.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.JobsOpenTerminalInFolderRequestItem]) (fnd.BulkResponse[orcapi.OpenSession], *util.HttpError) {
			var errors []*util.HttpError
			var responses []orcapi.OpenSession

			for _, item := range request.Items {
				jobCleanupShellSessions()

				shellSessionsMutex.Lock()
				tok := util.RandomToken(32)
				shellSessions[tok] = &ShellSession{Alive: true, Folder: item.Folder, UCloudUsername: info.Actor.Username}
				shellSessionsMutex.Unlock()
				responses = append(
					responses,
					orcapi.OpenSessionShell(item.Folder, 0, tok, cfg.Provider.Hosts.SelfPublic.ToWebSocketUrl()),
				)
			}

			if len(errors) > 0 {
				return fnd.BulkResponse[orcapi.OpenSession]{}, errors[0]
			} else {
				var response fnd.BulkResponse[orcapi.OpenSession]
				response.Responses = responses
				return response, nil
			}
		})

		type shellRequest struct {
			Type              string `json:"type"`
			SessionIdentifier string `json:"sessionIdentifier,omitempty"`
			Cols              int    `json:"cols,omitempty"`
			Rows              int    `json:"rows,omitempty"`
			Data              string `json:"data,omitempty"`
		}
		rpc.DefaultServer.Mux.HandleFunc(
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
		rpc.DefaultServer.Mux.HandleFunc(
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
						session := jobCreateFollowSession(requestMessage.StreamId, sendMessage, &alive, followRequest.Job)
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

		orcapi.JobsProviderRequestDynamicParameters.Handler(func(info rpc.RequestInfo, request orcapi.JobsProviderRequestDynamicParametersRequest) (orcapi.JobsProviderRequestDynamicParametersResponse, *util.HttpError) {
			fn := Jobs.RequestDynamicParameters

			var resp []orcapi.ApplicationParameter
			if fn != nil {
				resp = fn(request.Owner, &request.Application)
			}

			if resp == nil {
				resp = []orcapi.ApplicationParameter{}
			}

			return orcapi.JobsProviderRequestDynamicParametersResponse{Parameters: resp}, nil
		})

		orcapi.PublicIpsProviderCreate.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.PublicIp]) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
			var errors []*util.HttpError
			var providerIds []fnd.FindByStringId

			for _, item := range request.Items {
				if ResourceIsLocked(item.Resource, item.Specification.Product) {
					return fnd.BulkResponse[fnd.FindByStringId]{}, util.HttpErr(http.StatusPaymentRequired, "insufficient funds for %s", item.Specification.Product.Category)
				}
			}

			for _, item := range request.Items {
				PublicIpTrackNew(item)
				providerIds = append(providerIds, fnd.FindByStringId{})

				fn := Jobs.PublicIPs.Create
				if fn == nil {
					errors = append(errors, util.HttpErr(http.StatusBadRequest, "IP creation not supported"))
				} else {
					err := fn(&item)
					if err != nil {
						errors = append(errors, err)
					}
				}
			}

			if len(errors) == 1 && len(request.Items) == 1 {
				return fnd.BulkResponse[fnd.FindByStringId]{}, errors[0]
			} else {
				var response fnd.BulkResponse[fnd.FindByStringId]
				response.Responses = providerIds
				return response, nil
			}
		})

		orcapi.PublicIpsProviderDelete.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.PublicIp]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var errors []*util.HttpError
			var resp []util.Empty

			for _, item := range request.Items {
				fn := Jobs.PublicIPs.Delete
				if fn == nil {
					errors = append(errors, util.HttpErr(http.StatusBadRequest, "IP deletion not supported"))
					resp = append(resp, util.Empty{})
				} else {
					err := fn(&item)
					if err != nil {
						errors = append(errors, err)
						resp = append(resp, util.Empty{})
					} else {
						resp = append(resp, util.Empty{})
					}
				}
			}

			if len(errors) == 1 && len(request.Items) == 1 {
				return fnd.BulkResponse[util.Empty]{}, errors[0]
			} else {
				var response fnd.BulkResponse[util.Empty]
				response.Responses = resp
				return response, nil
			}
		})

		orcapi.PublicIpsProviderUpdateFirewall.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.PublicIpProviderUpdateFirewallRequest]) (util.Empty, *util.HttpError) {
			var errors []*util.HttpError

			for _, item := range request.Items {
				copied := item.PublicIp
				copied.Specification.Firewall = util.OptValue(item.Firewall)
				PublicIpTrackNew(copied)
			}

			if len(errors) == 1 && len(request.Items) == 1 {
				return util.Empty{}, errors[0]
			} else {
				return util.Empty{}, nil
			}
		})

		orcapi.PublicIpsProviderUpdateAcl.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.UpdatedAclWithResource[orcapi.PublicIp]]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			resp := fnd.BulkResponse[util.Empty]{}

			for _, item := range request.Items {
				publicIp := item.Resource

				permissions := publicIp.Permissions.Value
				for _, toDelete := range item.Deleted {
					for i, entry := range permissions.Others {
						if entry.Entity == toDelete {
							slices.Delete(permissions.Others, i, i+1)
						}
					}
				}

				for _, toAdd := range item.Added {
					found := false

					for i := 0; i < len(permissions.Others); i++ {
						entry := &permissions.Others[i]
						if entry.Entity == toAdd.Entity {
							for _, perm := range toAdd.Permissions {
								entry.Permissions = orcapi.PermissionsAdd(entry.Permissions, perm)
							}
							found = true
							break
						}
					}

					if !found {
						permissions.Others = append(permissions.Others, orcapi.ResourceAclEntry{
							Entity:      toAdd.Entity,
							Permissions: toAdd.Permissions,
						})
					}
				}

				PublicIpTrackNew(publicIp)

				resp.Responses = append(resp.Responses, util.Empty{})
			}

			return resp, nil
		})

		orcapi.IngressesProviderCreate.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.Ingress]) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
			var errors []*util.HttpError
			var providerIds []fnd.FindByStringId

			for _, item := range request.Items {
				LinkTrack(item)
				providerIds = append(providerIds, fnd.FindByStringId{})

				fn := Jobs.Ingresses.Create
				if fn == nil {
					errors = append(errors, util.HttpErr(http.StatusBadRequest, "Public link creation not supported"))
				} else {
					err := fn(&item)
					if err != nil {
						errors = append(errors, err)
					}
				}
			}

			if len(errors) == 1 && len(request.Items) == 1 {
				return fnd.BulkResponse[fnd.FindByStringId]{}, errors[0]
			} else {
				var response fnd.BulkResponse[fnd.FindByStringId]
				response.Responses = providerIds
				return response, nil
			}
		})

		orcapi.IngressesProviderDelete.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.Ingress]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var errors []*util.HttpError
			var resp []util.Empty

			for _, item := range request.Items {
				fn := Jobs.Ingresses.Delete
				if fn == nil {
					errors = append(errors, util.HttpErr(http.StatusBadRequest, "Public link deletion not supported"))
					resp = append(resp, util.Empty{})
				} else {
					err := fn(&item)
					if err != nil {
						errors = append(errors, err)
						resp = append(resp, util.Empty{})
					} else {
						resp = append(resp, util.Empty{})
					}
				}
			}

			if len(errors) == 1 && len(request.Items) == 1 {
				return fnd.BulkResponse[util.Empty]{}, errors[0]
			} else {
				var response fnd.BulkResponse[util.Empty]
				response.Responses = resp
				return response, nil
			}
		})

		orcapi.IngressesProviderUpdateAcl.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.UpdatedAclWithResource[orcapi.Ingress]]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			resp := fnd.BulkResponse[util.Empty]{}

			for _, item := range request.Items {
				ingress := item.Resource

				permissions := ingress.Permissions.Value
				for _, toDelete := range item.Deleted {
					for i, entry := range permissions.Others {
						if entry.Entity == toDelete {
							slices.Delete(permissions.Others, i, i+1)
						}
					}
				}

				for _, toAdd := range item.Added {
					found := false

					for i := 0; i < len(permissions.Others); i++ {
						entry := &permissions.Others[i]
						if entry.Entity == toAdd.Entity {
							for _, perm := range toAdd.Permissions {
								entry.Permissions = orcapi.PermissionsAdd(entry.Permissions, perm)
							}
							found = true
							break
						}
					}

					if !found {
						permissions.Others = append(permissions.Others, orcapi.ResourceAclEntry{
							Entity:      toAdd.Entity,
							Permissions: toAdd.Permissions,
						})
					}
				}

				LinkTrack(ingress)

				resp.Responses = append(resp.Responses, util.Empty{})
			}

			return resp, nil
		})

		orcapi.LicensesProviderCreate.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.License]) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
			var errors []*util.HttpError
			var providerIds []fnd.FindByStringId

			for _, item := range request.Items {
				if ResourceIsLocked(item.Resource, item.Specification.Product) {
					return fnd.BulkResponse[fnd.FindByStringId]{}, util.HttpErr(http.StatusPaymentRequired, "insufficient funds for %s", item.Specification.Product.Category)
				}
			}

			for _, item := range request.Items {
				LicenseTrack(item)
				providerIds = append(providerIds, fnd.FindByStringId{})

				fn := Jobs.Licenses.Create
				if fn == nil {
					errors = append(errors, util.HttpErr(http.StatusBadRequest, "License activation not supported"))
				} else {
					err := fn(&item)
					if err != nil {
						errors = append(errors, err)
					}
				}
			}

			if len(errors) == 1 && len(request.Items) == 1 {
				return fnd.BulkResponse[fnd.FindByStringId]{}, errors[0]
			} else {
				var response fnd.BulkResponse[fnd.FindByStringId]
				response.Responses = providerIds
				return response, nil
			}
		})

		orcapi.LicensesProviderDelete.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.License]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var errors []*util.HttpError
			var resp []util.Empty

			for _, item := range request.Items {
				fn := Jobs.Licenses.Delete
				if fn == nil {
					errors = append(errors, util.HttpErr(http.StatusBadRequest, "License deletion not supported"))
					resp = append(resp, util.Empty{})
				} else {
					err := fn(&item)
					if err != nil {
						errors = append(errors, err)
						resp = append(resp, util.Empty{})
					} else {
						resp = append(resp, util.Empty{})
					}
				}
			}

			if len(errors) == 1 && len(request.Items) == 1 {
				return fnd.BulkResponse[util.Empty]{}, errors[0]
			} else {
				var response fnd.BulkResponse[util.Empty]
				response.Responses = resp
				return response, nil
			}
		})

		orcapi.LicensesProviderUpdateAcl.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.UpdatedAclWithResource[orcapi.License]]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			resp := fnd.BulkResponse[util.Empty]{}

			for _, item := range request.Items {
				license := item.Resource

				permissions := license.Permissions.Value
				for _, toDelete := range item.Deleted {
					for i, entry := range permissions.Others {
						if entry.Entity == toDelete {
							slices.Delete(permissions.Others, i, i+1)
						}
					}
				}

				for _, toAdd := range item.Added {
					found := false

					for i := 0; i < len(permissions.Others); i++ {
						entry := &permissions.Others[i]
						if entry.Entity == toAdd.Entity {
							for _, perm := range toAdd.Permissions {
								entry.Permissions = orcapi.PermissionsAdd(entry.Permissions, perm)
							}
							found = true
							break
						}
					}

					if !found {
						permissions.Others = append(permissions.Others, orcapi.ResourceAclEntry{
							Entity:      toAdd.Entity,
							Permissions: toAdd.Permissions,
						})
					}
				}

				LicenseTrack(license)

				resp.Responses = append(resp.Responses, util.Empty{})
			}

			return resp, nil
		})

		orcapi.PrivateNetworksProviderCreate.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.PrivateNetwork]) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
			var errors []*util.HttpError
			var providerIds []fnd.FindByStringId

			for _, item := range request.Items {
				PrivateNetworkTrackNew(item)
				providerIds = append(providerIds, fnd.FindByStringId{})

				fn := Jobs.PrivateNetworks.Create
				if fn == nil {
					errors = append(errors, util.HttpErr(http.StatusBadRequest, "Private network creation not supported"))
				} else {
					err := fn(&item)
					if err != nil {
						errors = append(errors, err)
						_ = PrivateNetworkDelete(&item)
					}
				}
			}

			if len(errors) == 1 && len(request.Items) == 1 {
				return fnd.BulkResponse[fnd.FindByStringId]{}, errors[0]
			} else {
				var response fnd.BulkResponse[fnd.FindByStringId]
				response.Responses = providerIds
				return response, nil
			}
		})

		orcapi.PrivateNetworksProviderDelete.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.PrivateNetwork]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var errors []*util.HttpError
			var resp []util.Empty

			for _, item := range request.Items {
				fn := Jobs.PrivateNetworks.Delete
				if fn == nil {
					errors = append(errors, util.HttpErr(http.StatusBadRequest, "Private network deletion not supported"))
					resp = append(resp, util.Empty{})
				} else {
					err := fn(&item)
					if err != nil {
						errors = append(errors, err)
						resp = append(resp, util.Empty{})
					} else {
						resp = append(resp, util.Empty{})
					}
				}
			}

			if len(errors) == 1 && len(request.Items) == 1 {
				return fnd.BulkResponse[util.Empty]{}, errors[0]
			} else {
				var response fnd.BulkResponse[util.Empty]
				response.Responses = resp
				return response, nil
			}
		})

		orcapi.PrivateNetworksProviderUpdateAcl.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.UpdatedAclWithResource[orcapi.PrivateNetwork]]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			resp := fnd.BulkResponse[util.Empty]{}

			for _, item := range request.Items {
				network := item.Resource

				permissions := network.Permissions.Value
				for _, toDelete := range item.Deleted {
					for i, entry := range permissions.Others {
						if entry.Entity == toDelete {
							slices.Delete(permissions.Others, i, i+1)
						}
					}
				}

				for _, toAdd := range item.Added {
					found := false

					for i := 0; i < len(permissions.Others); i++ {
						entry := &permissions.Others[i]
						if entry.Entity == toAdd.Entity {
							for _, perm := range toAdd.Permissions {
								entry.Permissions = orcapi.PermissionsAdd(entry.Permissions, perm)
							}
							found = true
							break
						}
					}

					if !found {
						permissions.Others = append(permissions.Others, orcapi.ResourceAclEntry{
							Entity:      toAdd.Entity,
							Permissions: toAdd.Permissions,
						})
					}
				}

				PrivateNetworkTrackNew(network)

				resp.Responses = append(resp.Responses, util.Empty{})
			}

			return resp, nil
		})
	}

	if RunsServerCode() {
		rpc.DefaultServer.Mux.HandleFunc(
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

		orcapi.JobsProviderRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (fnd.BulkResponse[orcapi.JobSupport], *util.HttpError) {
			products := Jobs.RetrieveProducts()
			return fnd.BulkResponse[orcapi.JobSupport]{Responses: products}, nil
		})

		orcapi.PublicIpsProviderRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (fnd.BulkResponse[orcapi.PublicIpSupport], *util.HttpError) {
			var result []orcapi.PublicIpSupport
			fn := Jobs.PublicIPs.RetrieveProducts
			if fn != nil {
				result = fn()
			}

			return fnd.BulkResponse[orcapi.PublicIpSupport]{Responses: result}, nil
		})

		orcapi.IngressesProviderRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (fnd.BulkResponse[orcapi.IngressSupport], *util.HttpError) {
			var result []orcapi.IngressSupport
			fn := Jobs.Ingresses.RetrieveProducts
			if fn != nil {
				result = fn()
			}

			return fnd.BulkResponse[orcapi.IngressSupport]{Responses: result}, nil
		})

		orcapi.LicensesProviderRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (fnd.BulkResponse[orcapi.LicenseSupport], *util.HttpError) {
			var result []orcapi.LicenseSupport
			fn := Jobs.Licenses.RetrieveProducts
			if fn != nil {
				result = fn()
			}

			return fnd.BulkResponse[orcapi.LicenseSupport]{Responses: result}, nil
		})

		orcapi.PrivateNetworksProviderRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (fnd.BulkResponse[orcapi.PrivateNetworkSupport], *util.HttpError) {
			var result []orcapi.PrivateNetworkSupport
			fn := Jobs.PrivateNetworks.RetrieveProducts
			if fn != nil {
				result = fn()
			}

			return fnd.BulkResponse[orcapi.PrivateNetworkSupport]{Responses: result}, nil
		})

		rpc.DefaultServer.Mux.HandleFunc(
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
					sendError(writer, (&util.HttpError{
						StatusCode: http.StatusForbidden,
						Why:        "Forbidden",
					}).AsError())
					return
				}

				job, ok := JobRetrieve(idAndRank.JobId)
				if !ok {
					sendError(writer, (&util.HttpError{
						StatusCode: http.StatusInternalServerError,
						Why:        "Unknown job",
					}).AsError())
					return
				}

				handler := Jobs.HandleBuiltInVnc
				if handler == nil {
					sendError(writer, (&util.HttpError{
						StatusCode: http.StatusInternalServerError,
						Why:        "not supported",
					}).AsError())
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
	Job      *orcapi.Job                   `json:"job,omitempty"`      // init only
}

type jobsProviderFollowResponse struct {
	StreamId string              `json:"streamId"`
	Rank     int                 `json:"rank"`
	Stdout   util.Option[string] `json:"stdout"`
	Stderr   util.Option[string] `json:"stderr"`
	Channel  util.Option[string] `json:"channel"`
}

func jobCreateFollowSession(
	wsStreamId string,
	writeMessage func(message any) error,
	alive *bool,
	job *orcapi.Job,
) *FollowJobSession {
	jobCleanupFollowSessions()

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

func jobCleanupFollowSessions() {
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

func jobCleanupShellSessions() {
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
		job, ok := JobRetrieve(r.Payload.JobId)
		if !ok {
			return ipc.Response[string]{
				StatusCode: http.StatusNotFound,
				Payload:    "",
			}
		}

		if !BelongsToWorkspace(orcapi.ResourceOwnerToWalletOwner(job.Resource), r.Uid) {
			return ipc.Response[string]{
				StatusCode: http.StatusNotFound,
				Payload:    "",
			}
		}

		result, err := IngressRegisterWithJob(job, r.Payload.Rank, r.Payload.Target, r.Payload.RequestedSuffix, r.Payload.Flags)
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

func IngressRegisterWithJob(job *orcapi.Job, rank int, target cfg.HostInfo, requestedSuffix util.Option[string], flags RegisteredIngressFlags) (string, *util.HttpError) {
	isWeb := (flags & RegisteredIngressFlagsWeb) != 0
	isVnc := (flags & RegisteredIngressFlagsVnc) != 0

	if !isWeb && !isVnc {
		return "", util.ServerHttpError("must specify either RegisteredIngressFlagsWeb or RegisteredIngressFlagsVnc")
	}

	if !RunsServerCode() {
		result, ierr := jobsRegisterIngressCall.Invoke(jobRegisteredIngress{
			JobId:           job.Id,
			Target:          target,
			RequestedSuffix: requestedSuffix,
			Flags:           flags,
		})

		return result, util.HttpErrorFromErr(ierr)
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
			jobRoutesRefresh()
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
			return "", util.ServerHttpError("unhandled case %v", flags)
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

	jobRoutesRefresh()
}

func jobRoutesRefresh() {
	allJobs := JobsListServer()
	allJobsById := map[string]*orcapi.Job{}
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
