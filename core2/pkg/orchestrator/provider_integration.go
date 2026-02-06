package orchestrator

import (
	"math/rand"
	"net/http"
	"slices"
	"strings"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initProviderIntegration() {
	orcapi.ProviderIntegrationBrowse.Handler(func(info rpc.RequestInfo, request orcapi.ProviderIntegrationBrowseRequest) (fndapi.PageV2[orcapi.ProviderIntegrationBrowseResponse], *util.HttpError) {
		return ProviderIntegrationBrowse(info.Actor)
	})

	orcapi.ProviderIntegrationConnect.Handler(func(info rpc.RequestInfo, request orcapi.ProviderIntegrationConnectRequest) (orcapi.ProviderIntegrationConnectResponse, *util.HttpError) {
		list, err := ProviderIntegrationBrowse(info.Actor)
		if err != nil {
			return orcapi.ProviderIntegrationConnectResponse{}, err
		} else {
			found := false
			connected := false
			for _, item := range list.Items {
				if item.Provider == request.Provider {
					found = true
					connected = item.Connected
					break
				}
			}

			if !found {
				return orcapi.ProviderIntegrationConnectResponse{}, util.HttpErr(http.StatusForbidden, "you cannot connect to this provider")
			} else if connected {
				return orcapi.ProviderIntegrationConnectResponse{}, util.HttpErr(http.StatusForbidden, "you are already connected to this provider")
			} else {
				resp, err := InvokeProvider(request.Provider, orcapi.ProviderIntegrationPConnect,
					orcapi.ProviderIntegrationPFindByUser{Username: info.Actor.Username}, ProviderCallOpts{})

				if err != nil {
					return orcapi.ProviderIntegrationConnectResponse{}, util.HttpErr(http.StatusBadGateway, "could not contact provider: %s", err)
				} else {
					return orcapi.ProviderIntegrationConnectResponse{RedirectTo: resp.RedirectTo}, nil
				}
			}
		}
	})

	orcapi.ProviderIntegrationClearConnection.Handler(func(info rpc.RequestInfo, request orcapi.ProviderIntegrationClearConnectionRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, ProviderIntegrationClearConnection(info.Actor.Username, request.Provider, true)
	})

	orcapi.ProviderIntegrationCtrlClearConnection.Handler(func(info rpc.RequestInfo, request orcapi.ProviderIntegrationCtrlFindByUser) (util.Empty, *util.HttpError) {
		providerId, ok := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		if !ok {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		return util.Empty{}, ProviderIntegrationClearConnection(request.Username, providerId, false)
	})

	orcapi.ProviderIntegrationCtrlApproveConnection.Handler(func(info rpc.RequestInfo, request orcapi.ProviderIntegrationCtrlFindByUser) (util.Empty, *util.HttpError) {
		providerId, ok := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		if !ok {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		providerIntegrationMarkAsConnected(request.Username, providerId)
		return util.Empty{}, nil
	})
}

func ProviderIntegrationClearConnection(username string, provider string, contactProvider bool) *util.HttpError {
	didRemove := db.NewTx(func(tx *db.Transaction) bool {
		_, ok := db.Get[struct{ Username string }](
			tx,
			`
				delete from provider.connected_with cw
				using provider.providers p
				where 
					p.unique_name = :provider
					and cw.provider_id = p.resource 
					and cw.username = :username
				returning username
		    `,
			db.Params{
				"provider": provider,
				"username": username,
			},
		)

		return ok
	})

	if didRemove && contactProvider {
		_, err := InvokeProvider(provider, orcapi.ProviderIntegrationPDisconnect,
			orcapi.ProviderIntegrationPFindByUser{Username: username}, ProviderCallOpts{})

		if err != nil {
			providerIntegrationMarkAsConnected(username, provider)
			return err
		}
	}

	providerIntegrationClearConnectedToCache(username)
	return nil
}

func providerIntegrationMarkAsConnected(username string, provider string) {
	manifest, _, ok := ManifestByProvider(provider)
	expiresAt := util.Option[time.Time]{}
	if ok && manifest.ExpireAfterMs.Present {
		expiresAt.Set(time.Now().Add(time.Duration(manifest.ExpireAfterMs.Value) * time.Millisecond))
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into provider.connected_with(username, provider_id, expires_at) 
				select :username, p.resource, :expires_at
				from provider.providers p
				where unique_name = :provider
		    `,
			db.Params{
				"username":   username,
				"provider":   provider,
				"expires_at": expiresAt.Sql(),
			},
		)
	})

	providerIntegrationClearConnectedToCache(username)
}

func ProviderIntegrationBrowse(actor rpc.Actor) (fndapi.PageV2[orcapi.ProviderIntegrationBrowseResponse], *util.HttpError) {
	providers, err := accapi.FindRelevantProviders.Invoke(fndapi.BulkRequestOf(accapi.FindRelevantProvidersRequest{
		Username:         actor.Username,
		IncludeFreeToUse: util.OptValue(false),
		UseProject:       false,
	}))

	if err != nil {
		return fndapi.PageV2[orcapi.ProviderIntegrationBrowseResponse]{}, util.HttpErr(http.StatusInternalServerError, "could not fetch provider list")
	} else {
		connectedTo := providerIntegrationGetProvidersConnectedTo(actor.Username)

		var result []orcapi.ProviderIntegrationBrowseResponse
		for _, provider := range providers.Responses[0].Providers {
			manifest, _, ok := ManifestByProvider(provider)

			_, isActuallyConnected := connectedTo[provider]
			isConnected := isActuallyConnected || provider == "aau" || provider == "ucloud" || (ok && !manifest.Enabled)

			result = append(result, orcapi.ProviderIntegrationBrowseResponse{
				Provider:      provider, // This used to be the numeric id
				Connected:     isConnected,
				ProviderTitle: provider,
			})
		}

		slices.SortFunc(result, func(a, b orcapi.ProviderIntegrationBrowseResponse) int {
			return strings.Compare(a.ProviderTitle, b.ProviderTitle)
		})

		return fndapi.PageV2[orcapi.ProviderIntegrationBrowseResponse]{
			Items:        result,
			ItemsPerPage: len(result),
		}, nil
	}
}

var providerIntegrationConnectedTo = util.NewCache[string, map[string]util.Empty](1 * time.Hour)

func providerIntegrationClearConnectedToCache(username string) {
	providerIntegrationConnectedTo.Invalidate(username)
}

func providerIntegrationGetProvidersConnectedTo(username string) map[string]util.Empty {
	result, ok := providerIntegrationConnectedTo.Get(username, func() (map[string]util.Empty, error) {
		return db.NewTx(func(tx *db.Transaction) map[string]util.Empty {
			if rand.Intn(100) == 1 {
				db.Exec(
					tx,
					`
						delete from provider.connected_with
						where expires_at is not null and now() > expires_at
				    `,
					db.Params{},
				)
			}

			rows := db.Select[struct{ ProviderName string }](
				tx,
				`
					select p.unique_name as provider_name
					from 
						provider.connected_with cw
						join provider.providers p on cw.provider_id = p.resource
					where
						cw.username = :username
						and (
							expires_at is null
							or now() >= expires_at
						)
			    `,
				db.Params{
					"username": username,
				},
			)

			result := map[string]util.Empty{}
			for _, row := range rows {
				result[row.ProviderName] = util.Empty{}
			}
			return result
		}), nil
	})

	if !ok {
		return map[string]util.Empty{}
	} else {
		return result
	}
}
