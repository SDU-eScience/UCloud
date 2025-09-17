package orchestrator

import (
	"fmt"
	lru "github.com/hashicorp/golang-lru/v2/expirable"
	"net/http"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var resourceAllocationCache = lru.NewLRU[string, util.Empty](2048, nil, 10*time.Minute)

func resourceRefKey(actor rpc.Actor, category string, provider string) string {
	ownerRef := string(actor.Project.GetOrDefault(rpc.ProjectId(actor.Username)))
	return ownerRef + fmt.Sprintf("\n%s@%s", category, provider)
}

func ResourceValidateAllocation(actor rpc.Actor, productRef accapi.ProductReference) *util.HttpError {
	product, ok := productFromCache(productRef)
	if ok && product.Category.FreeToUse {
		return ResourceValidateAllocation(actor, accapi.ProductReference{Provider: productRef.Provider})
	}

	_, ok = resourceAllocationCache.Get(resourceRefKey(actor, productRef.Category, productRef.Provider))
	if !ok {
		resp, err := accapi.WalletsBrowseInternal.Invoke(accapi.WalletsBrowseInternalRequest{
			Owner: accapi.WalletOwnerFromIds(actor.Username, string(actor.Project.Value)),
		})

		if err != nil {
			return util.HttpErr(
				http.StatusBadGateway,
				"could not validate that you have access to '%v' - try again later",
				util.OptStringIfNotEmpty(productRef.Category).GetOrDefault(productRef.Provider),
			)
		}

		for _, wallet := range resp.Wallets {
			if len(wallet.AllocationGroups) > 0 {
				resourceAllocationCache.Add(resourceRefKey(actor, wallet.PaysFor.Name, wallet.PaysFor.Provider), util.Empty{})

				// NOTE(Dan): An empty category entry is used to signify access to the provider through any allocation
				resourceAllocationCache.Add(resourceRefKey(actor, "", wallet.PaysFor.Provider), util.Empty{})
			}
		}

		_, ok = resourceAllocationCache.Get(resourceRefKey(actor, productRef.Category, productRef.Provider))
	}

	if !ok {
		return util.HttpErr(
			http.StatusPaymentRequired,
			"you do not have any valid allocations for '%v'",
			util.OptStringIfNotEmpty(productRef.Category).GetOrDefault(productRef.Provider),
		)
	} else {
		return nil
	}
}
