package orchestrator

import (
	"context"
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
	"sync"
	"time"

	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

type jobUcxSessionState struct {
	appUcxBaseState
	Mu      sync.RWMutex
	job     orcapi.Job
	actor   rpc.Actor
	reqInfo orcapi.AppUcxConnectJobRequest
}

func initJobUcx() {
	orcapi.AppUcxConnectJob.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		proxy := ucx.NewProxy("ws://pending")
		state := &jobUcxSessionState{
			actor: rpc.Actor{Role: rpc.RoleGuest},
		}

		proxy.RegisterUpstreamSelector(func(ctx context.Context, downstreamToken string, downstreamSysHello string) ucx.ProxyUpstreamSelection {
			{
				reqInfo := orcapi.AppUcxConnectJobRequest{}
				if err := json.Unmarshal([]byte(downstreamSysHello), &reqInfo); err != nil {
					log.Warn("UCX job: invalid syshello payload: %v", err)
					return ucx.ProxyUpstreamSelection{
						Allowed: false,
					}
				}

				tok := strings.SplitN(downstreamToken, "\n", 2)
				if len(tok) != 2 {
					log.Warn("UCX job: invalid downstream token format")
					return ucx.ProxyUpstreamSelection{
						Allowed: false,
					}
				}

				bearerActor, authErr := rpc.BearerAuthenticator(tok[0], tok[1])
				if authErr != nil {
					log.Warn("UCX job: downstream bearer authentication failed: %v", authErr)
					return ucx.ProxyUpstreamSelection{
						Allowed: false,
					}
				}

				if bearerActor.Role&rpc.RolesEndUser == 0 {
					log.Warn("UCX job: downstream actor is not an end user")
					return ucx.ProxyUpstreamSelection{
						Allowed: false,
					}
				}

				state.actor = bearerActor
				state.reqInfo = reqInfo
			}

			job, err := JobsRetrieve(state.actor, state.reqInfo.JobId, orcapi.JobFlags{
				IncludeParameters:  true,
				IncludeApplication: true,
			})
			if err != nil {
				log.Info("UCX job: not allowed to retrieve job %v %#v", state.reqInfo.JobId, state.actor)
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			if job.Specification.Labels == nil {
				job.Specification.Labels = map[string]string{}
			}

			port := 0
			portLabel, hasPortLabel := job.Specification.Labels[resourceLabelUcxPort]
			if !hasPortLabel && job.Specification.Application.Name != reservedSyncthingApplication {
				log.Info("UCX job: This job has no UCX port associated with it")
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}
			if hasPortLabel {
				parsedPort, gerr := strconv.Atoi(portLabel)
				if gerr != nil {
					log.Info("UCX job: Malformed port label")
					return ucx.ProxyUpstreamSelection{
						Allowed: false,
					}
				}
				port = parsedPort

				if !ucxUiJobAvailable(job.Id) {
					if alternative, alternativePort, found := stacksUcxUiAlternativeJob(state.actor, job); found {
						log.Info("UCX job: the job %s did not answer recently, connecting to %s", job.Id, alternative.Id)
						job = alternative
						port = alternativePort
					}
				}
			}

			providerId := job.Specification.Product.Provider
			client, ok := providerClient(providerId)
			if !ok {
				log.Warn("UCX job: provider client not found for %q", providerId)
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			upstreamUrl := strings.ReplaceAll(client.BasePath, "http://", "ws://")
			upstreamUrl = strings.ReplaceAll(upstreamUrl, "https://", "wss://")
			upstreamUrl = fmt.Sprintf(
				"%s/ucloud/%s/hpc/apps/ucx/connectJob",
				upstreamUrl,
				providerId,
			)

			upstreamTok := client.RetrieveAccessTokenOrRefresh()
			if upstreamTok == "" {
				log.Warn("UCX job: empty provider access token for %q", providerId)
				return ucx.ProxyUpstreamSelection{Allowed: false}
			}

			sysHello, marshalErr := json.Marshal(orcapi.AppUcxConnectJobProviderRequest{
				Job:  job,
				Port: port,
			})
			if marshalErr != nil {
				log.Warn("UCX job: failed to marshal provider syshello: %v", marshalErr)
				return ucx.ProxyUpstreamSelection{Allowed: false}
			}

			state.Mu.Lock()
			state.job = job
			state.AllowStackCreation = false
			state.Stacks = map[string]util.Empty{}
			stackId, ok := job.Specification.Labels[orcapi.ResourceLabelStackInstance]
			if ok {
				state.Stacks[stackId] = util.Empty{}
			}
			state.appUcxBaseState.Actor = func() rpc.Actor {
				state.Mu.RLock()
				result := state.actor
				state.Mu.RUnlock()
				return result
			}
			state.appUcxBaseState.Provider = func() string {
				state.Mu.RLock()
				result := state.job.Specification.Product.Provider
				state.Mu.RUnlock()
				return result
			}
			state.appUcxBaseState.SessionJob = func() (orcapi.Job, bool) {
				state.Mu.RLock()
				defer state.Mu.RUnlock()
				if state.job.Id == "" {
					return orcapi.Job{}, false
				}
				return state.job, true
			}
			state.Mu.Unlock()

			return ucx.ProxyUpstreamSelection{
				Allowed:               true,
				UpstreamUrl:           upstreamUrl,
				UpstreamToken:         upstreamTok,
				UpstreamTokenInBearer: true,
				UpstreamSysHello:      string(sysHello),
			}
		})

		appUcxResourceHandlers(&state.appUcxBaseState, proxy)

		startedAt := time.Now()
		runErr := proxy.Run(context.Background(), info.WebSocket)

		state.Mu.RLock()
		sessionJobId := state.job.Id
		state.Mu.RUnlock()
		ucxUiNoteSessionEnd(sessionJobId, time.Since(startedAt))

		if runErr != nil {
			log.Warn("UCX job: proxy failed: %v", runErr)
			return util.Empty{}, util.ServerHttpError("Internal error")
		}
		return util.Empty{}, nil
	})
}

const (
	resourceLabelUcxPort         = "ucloud.dk/ucxport"
	reservedSyncthingApplication = "syncthing"
)
