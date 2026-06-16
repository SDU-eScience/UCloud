package accounting

import (
	"time"

	lru "github.com/hashicorp/golang-lru/v2/expirable"
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initAccounting() {
	//accountingLoad()
	//go accountingProcessTasks()

	accapi.RootAllocate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.RootAllocateRequest]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		/*
			var result []fndapi.FindByStringId
			for _, reqItem := range request.Items {
				id, err := RootAllocate(info.Actor, reqItem)
				if err != nil {
					return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
				} else {
					result = append(result, fndapi.FindByStringId{Id: id})
				}
			}
			return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: result}, nil
		*/
		return fndapi.BulkResponse[fndapi.FindByStringId]{}, nil
	})

	accapi.UpdateAllocation.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.UpdateAllocationRequest]) (util.Empty, *util.HttpError) {
		//return AllocationUpdate(info.Actor, request.Items)
		return util.Empty{}, nil
	})

	accapi.ReportUsage.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.ReportUsageRequest]) (fndapi.BulkResponse[bool], *util.HttpError) {
		now := time.Now()
		var result []bool
		for _, reqItem := range request.Items {
			if true {
				return fndapi.BulkResponse[bool]{}, nil // TODO Validate actor
			}
			resp, err := UsageReport(now, reqItem)
			if err != nil {
				return fndapi.BulkResponse[bool]{}, err
			} else {
				result = append(result, resp)
			}
		}
		return fndapi.BulkResponse[bool]{Responses: result}, nil
	})

	actorToOwner := func(actor rpc.Actor) accapi.WalletOwner {
		if actor.Project.Present {
			return accapi.WalletOwnerProject(string(actor.Project.Value))
		} else {
			return accapi.WalletOwnerUser(actor.Username)
		}
	}

	accapi.WalletsBrowse.Handler(func(info rpc.RequestInfo, request accapi.WalletsBrowseRequest) (fndapi.PageV2[accapi.WalletV2], *util.HttpError) {
		now := time.Now()
		return WalletsBrowsePage(now, request, WalletBrowseFilter{
			Owner: util.OptValue(actorToOwner(info.Actor)),
		}), nil
	})

	accapi.WalletsBrowseInternal.Handler(func(info rpc.RequestInfo, request accapi.WalletsBrowseInternalRequest) (accapi.WalletsBrowseInternalResponse, *util.HttpError) {
		/*
			if !validateOwner(request.Owner) {
				return accapi.WalletsBrowseInternalResponse{}, util.HttpErr(http.StatusNotFound, "unknown owner")
			} else {
				wallets := internalRetrieveWallets(time.Now(), request.Owner.Reference(), walletFilter{
					RequireActive: false,
				})

				return accapi.WalletsBrowseInternalResponse{Wallets: wallets}, nil
			}
		*/
		return accapi.WalletsBrowseInternalResponse{}, nil
	})

	accapi.CheckProviderUsable.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.CheckProviderUsableRequest]) (fndapi.BulkResponse[accapi.CheckProviderUsableResponse], *util.HttpError) {
		/*
			now := time.Now()

			providerId, ok := strings.CutPrefix(fndapi.ProviderSubjectPrefix, info.Actor.Username)
			if !ok {
				return fndapi.BulkResponse[accapi.CheckProviderUsableResponse]{}, util.HttpErr(http.StatusForbidden, "forbidden")
			}

			var result []accapi.CheckProviderUsableResponse

			for _, reqItem := range request.Items {
				ok = reqItem.Category.Provider == providerId && validateOwner(reqItem.Owner)
				wallet := AccWalletId(0)
				maxUsable := int64(0)

				if ok {
					wallet, ok = internalWalletByReferenceAndCategory(now, reqItem.Owner.Reference(), reqItem.Category)
				}

				if ok {
					maxUsable, ok = internalMaxUsable(now, wallet)
				}

				if ok {
					result = append(result, accapi.CheckProviderUsableResponse{MaxUsable: maxUsable})
				} else {
					return fndapi.BulkResponse[accapi.CheckProviderUsableResponse]{}, util.HttpErr(http.StatusBadRequest, "invalid request")
				}
			}

			return fndapi.BulkResponse[accapi.CheckProviderUsableResponse]{Responses: result}, nil
		*/
		return fndapi.BulkResponse[accapi.CheckProviderUsableResponse]{}, nil
	})

	accapi.FindRelevantProviders.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.FindRelevantProvidersRequest]) (fndapi.BulkResponse[accapi.FindRelevantProvidersResponse], *util.HttpError) {
		/*
			now := time.Now()

			var result []accapi.FindRelevantProvidersResponse

			for _, reqItem := range request.Items {
				var owners []accapi.WalletOwner

				if reqItem.UseProject {
					owner := accapi.WalletOwnerUser(reqItem.Username)
					if reqItem.Project.Present {
						owner = accapi.WalletOwnerProject(reqItem.Project.Value)
					}

					owners = append(owners, owner)
				} else {
					owners = append(owners, accapi.WalletOwnerUser(reqItem.Username))
					actor, ok := rpc.LookupActor(reqItem.Username)
					if ok {
						for project := range actor.Membership {
							owners = append(owners, accapi.WalletOwnerProject(string(project)))
						}
					}
				}

				providers := map[string]util.Empty{}

				for _, owner := range owners {
					if validateOwner(owner) {
						wallets := internalRetrieveWallets(now, owner.Reference(), walletFilter{
							ProductType:   reqItem.FilterProductType,
							RequireActive: true,
						})

						// TODO free to use

						for _, w := range wallets {
							providers[w.PaysFor.Provider] = util.Empty{}
						}

					} else {
						return fndapi.BulkResponse[accapi.FindRelevantProvidersResponse]{}, util.HttpErr(http.StatusBadRequest, "bad owner supplied")
					}
				}

				var providerArr []string
				for providerId := range providers {
					providerArr = append(providerArr, providerId)
				}

				result = append(result, accapi.FindRelevantProvidersResponse{Providers: providerArr})
			}

			return fndapi.BulkResponse[accapi.FindRelevantProvidersResponse]{Responses: result}, nil
		*/
		return fndapi.BulkResponse[accapi.FindRelevantProvidersResponse]{}, nil
	})

	accapi.FindAllProviders.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[accapi.FindAllProvidersRequest]) (fndapi.BulkResponse[accapi.FindAllProvidersResponse], *util.HttpError) {
		var result []accapi.FindAllProvidersResponse

		categories := ProductCategories()
		for _, reqItem := range request.Items {
			providers := map[string]util.Empty{}

			for _, cat := range categories {
				if cat.FreeToUse || reqItem.IncludeFreeToUse.GetOrDefault(false) {
					if !reqItem.FilterProductType.Present || reqItem.FilterProductType.Value == cat.ProductType {
						providers[cat.Provider] = util.Empty{}
					}
				}
			}

			var resp accapi.FindAllProvidersResponse
			for provider := range providers {
				resp.Providers = append(resp.Providers, provider)
			}
		}

		return fndapi.BulkResponse[accapi.FindAllProvidersResponse]{Responses: result}, nil
	})
}

var validatedOwners = lru.NewLRU[string, util.Empty](1024*4, nil, 10*time.Minute)

func validateOwner(owner accapi.WalletOwner) bool {
	_, valid := validatedOwners.Get(owner.Reference())
	if valid {
		return true
	}

	result := false
	switch owner.Type {
	case accapi.WalletOwnerTypeUser:
		_, ok := rpc.LookupActor(owner.Username)
		result = ok

	case accapi.WalletOwnerTypeProject:
		_, err := fndapi.ProjectRetrieveMetadata.Invoke(fndapi.FindByStringId{
			Id: owner.ProjectId,
		})
		result = err == nil
	}

	if result {
		validatedOwners.Add(owner.Reference(), util.Empty{})
	}

	return result
}
