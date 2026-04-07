package orchestrator

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"slices"
	"strings"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

type appUcxSessionState struct {
	appUcxBaseState
	actor   rpc.Actor
	reqInfo orcapi.AppUcxConnectRequest
}

func initAppUcx() {
	orcapi.AppUcxConnect.Handler(func(info rpc.RequestInfo, _ util.Empty) (util.Empty, *util.HttpError) {
		proxy := ucx.NewProxy("ws://pending")
		state := &appUcxSessionState{
			actor: rpc.Actor{Role: rpc.RoleGuest},
			appUcxBaseState: appUcxBaseState{
				Stacks:             map[string]util.Empty{},
				AllowStackCreation: true,
			},
		}
		state.appUcxBaseState.Actor = func() rpc.Actor {
			state.Mu.RLock()
			result := state.actor
			state.Mu.RUnlock()
			return result
		}
		state.appUcxBaseState.Provider = func() string {
			state.Mu.RLock()
			provider := state.reqInfo.Provider.GetOrDefault("")
			state.Mu.RUnlock()
			return provider
		}

		proxy.RegisterUpstreamSelector(func(ctx context.Context, downstreamToken string, downstreamSysHello string) ucx.ProxyUpstreamSelection {
			reqInfo := orcapi.AppUcxConnectRequest{}
			if err := json.Unmarshal([]byte(downstreamSysHello), &reqInfo); err != nil {
				log.Warn("UCX core: invalid syshello payload: %v", err)
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			tok := strings.SplitN(downstreamToken, "\n", 2)
			if len(tok) != 2 {
				log.Warn("UCX core: invalid downstream token format")
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			bearerActor, authErr := rpc.BearerAuthenticator(tok[0], tok[1])
			if authErr != nil {
				log.Warn("UCX core: downstream bearer authentication failed: %v", authErr)
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			if bearerActor.Role&rpc.RolesEndUser == 0 {
				log.Warn("UCX core: downstream actor is not an end user")
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			app, ok := AppRetrieve(bearerActor, reqInfo.Name, reqInfo.Version, AppDiscoveryAll, 0)
			if !ok {
				log.Warn("UCX core: app not found: %s@%s", reqInfo.Name, reqInfo.Version)
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			if !app.Invocation.Tool.Tool.Present {
				log.Warn("UCX core: resolved app has no tool payload: %s@%s", reqInfo.Name, reqInfo.Version)
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			if app.Invocation.Tool.Tool.Value.Description.Backend != orcapi.ToolBackendUcx {
				log.Warn("UCX core: app backend is not UCX: %s@%s (%s)", reqInfo.Name, reqInfo.Version, app.Invocation.Tool.Tool.Value.Description.Backend)
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			providerResp, err := accapi.FindRelevantProviders.Invoke(fndapi.BulkRequestOf(accapi.FindRelevantProvidersRequest{
				Username: bearerActor.Username,
				Project: util.OptMap(bearerActor.Project, func(value rpc.ProjectId) string {
					return string(value)
				}),
				UseProject:        bearerActor.Project.Present,
				FilterProductType: util.OptValue(accapi.ProductTypeCompute),
			}))

			if err != nil || len(providerResp.Responses) == 0 {
				log.Warn("UCX core: failed to find relevant providers: err=%v responseCount=%d", err, len(providerResp.Responses))
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			providers := providerResp.Responses[0].Providers
			if len(providers) == 0 {
				log.Warn("UCX core: no providers available for actor=%s", bearerActor.Username)
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			if reqInfo.Provider.Present {
				found := false
				for _, provider := range providers {
					if provider == reqInfo.Provider.Value {
						found = true
						break
					}
				}

				if !found {
					log.Warn("UCX core: requested provider %q is not available", reqInfo.Provider.Value)
					return ucx.ProxyUpstreamSelection{
						Allowed: false,
					}
				}
			} else {
				slices.Sort(providers)
				reqInfo.Provider.Set(providers[0])
			}

			client, ok := providerClient(reqInfo.Provider.Value)
			if !ok {
				log.Warn("UCX core: provider client not found for %q", reqInfo.Provider.Value)
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			upstreamUrl := strings.ReplaceAll(client.BasePath, "http://", "ws://")
			upstreamUrl = strings.ReplaceAll(upstreamUrl, "https://", "wss://")
			upstreamUrl = fmt.Sprintf(
				"%s/ucloud/%s/hpc/apps/ucx/connect",
				upstreamUrl,
				reqInfo.Provider.Value,
			)

			upstreamTok := client.RetrieveAccessTokenOrRefresh()
			if upstreamTok == "" {
				log.Warn("UCX core: empty provider access token for %q", reqInfo.Provider.Value)
				return ucx.ProxyUpstreamSelection{Allowed: false}
			}

			sysHello, marshalErr := json.Marshal(orcapi.AppUcxConnectProviderRequest{
				Application: app,
				Owner: orcapi.ResourceOwner{
					CreatedBy: bearerActor.Username,
					Project: util.OptMap(bearerActor.Project, func(value rpc.ProjectId) string {
						return string(value)
					}),
				},
			})
			if marshalErr != nil {
				log.Warn("UCX core: failed to marshal provider syshello: %v", marshalErr)
				return ucx.ProxyUpstreamSelection{Allowed: false}
			}

			state.Mu.Lock()
			state.actor = bearerActor
			state.reqInfo = reqInfo
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

		ucxapi.StackAvailable.HandlerProxy(proxy, func(ctx context.Context, request fndapi.FindByStringId) (bool, error) {
			actor := state.Actor()
			_, err := StacksRetrieve(actor, request.Id)
			return err != nil && err.StatusCode == http.StatusNotFound, nil
		})

		ucxapi.Core.HandlerProxy(proxy, func(ctx context.Context, request ucxapi.Message) (ucxapi.Message, error) {
			actor := state.Actor()
			log.Info("Got a message from '%v': %s", actor, request)
			return ucxapi.Message{"Hello from the Core!"}, nil
		})

		if gerr := proxy.Run(context.Background(), info.WebSocket); gerr != nil {
			log.Warn("UCX core: proxy failed: %v", gerr)
			return util.Empty{}, util.ServerHttpError("Internal error")
		} else {
			return util.Empty{}, nil
		}
	})
}
