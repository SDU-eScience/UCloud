package orchestrator

import (
	"context"
	"encoding/json"
	"fmt"
	"slices"
	"strings"

	"ucloud.dk/core/pkg/orchestrator/ucx"
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initAppUcx() {
	orcapi.AppUcxConnect.Handler(func(info rpc.RequestInfo, _ util.Empty) (util.Empty, *util.HttpError) {
		var err *util.HttpError
		actor := rpc.Actor{Role: rpc.RoleGuest}
		var request orcapi.AppUcxConnectRequest

		proxy := ucx.NewProxy("ws://pending")
		proxy.RegisterUpstreamSelector(func(ctx context.Context, downstreamToken string, downstreamSysHello string) ucx.ProxyUpstreamSelection {
			gerr := json.Unmarshal([]byte(downstreamSysHello), &request)
			if gerr != nil {
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			tok := strings.Split(downstreamToken, "\n")
			if len(tok) != 2 {
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			actor, err = rpc.BearerAuthenticator(tok[0], tok[1])

			if err != nil {
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			if actor.Role&rpc.RolesEndUser == 0 {
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			app, ok := AppRetrieve(actor, request.Name, request.Version, AppDiscoveryAll, 0)
			if !ok || app.Invocation.Tool.Tool.Value.Description.Backend != orcapi.ToolBackendUcx {
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			providerResp, err := accapi.FindRelevantProviders.Invoke(fndapi.BulkRequestOf(accapi.FindRelevantProvidersRequest{
				Username: actor.Username,
				Project: util.OptMap(actor.Project, func(value rpc.ProjectId) string {
					return string(value)
				}),
				UseProject:        actor.Project.Present,
				FilterProductType: util.OptValue(accapi.ProductTypeCompute),
			}))

			if err != nil || len(providerResp.Responses) == 0 {
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			providers := providerResp.Responses[0].Providers

			if request.Provider.Present {
				found := false
				for _, provider := range providers {
					if provider == request.Provider.Value {
						found = true
						break
					}
				}

				if !found {
					return ucx.ProxyUpstreamSelection{
						Allowed: false,
					}
				}
			} else {
				slices.Sort(providers)
				request.Provider.Set(providers[0])
			}

			client, ok := providerClient(request.Provider.Value)
			if !ok {
				return ucx.ProxyUpstreamSelection{
					Allowed: false,
				}
			}

			upstreamUrl := strings.ReplaceAll(client.BasePath, "http://", "ws://")
			upstreamUrl = strings.ReplaceAll(upstreamUrl, "https://", "wss://")
			upstreamUrl = fmt.Sprintf(
				"%s/ucloud/%s/hpc/apps/ucx/connect",
				upstreamUrl,
				request.Provider.Value,
			)

			upstreamTok := client.RetrieveAccessTokenOrRefresh()

			sysHello, _ := json.Marshal(orcapi.AppUcxConnectProviderRequest{Application: app})

			return ucx.ProxyUpstreamSelection{
				Allowed:          true,
				UpstreamUrl:      upstreamUrl,
				UpstreamToken:    upstreamTok,
				UpstreamSysHello: string(sysHello),
			}
		})

		gerr := proxy.Run(context.Background(), info.WebSocket)
		if gerr != nil {
			return util.Empty{}, util.ServerHttpError("Internal error")
		} else {
			return util.Empty{}, nil
		}
	})
}
