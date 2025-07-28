package orchestrator

import (
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func ResourceCreateThroughProvider[T any](
	actor rpc.Actor,
	typeName string,
	product accapi.ProductReference,
	extra any,
	call rpc.Call[fndapi.BulkRequest[T], fndapi.BulkResponse[fndapi.FindByStringId]],
) (T, *util.HttpError) {
	var t T

	id, resc, err := ResourceCreate[T](actor, typeName, util.OptValue(product), extra)
	if err != nil {
		return t, err
	}

	resp, err := InvokeProvider(product.Provider, call, fndapi.BulkRequestOf(resc), ProviderCallOpts{
		Username: util.OptValue(actor.Username),
		Reason:   util.OptValue("Creating resource: " + typeName),
	})

	if err == nil {
		providerId := resp.Responses[0].Id
		if providerId != "" {
			ResourceSystemUpdate(drive, id, func(r *resource, mapped orcapi.Drive) {
				r.ProviderId.Set(providerId)
			})
		}

		ResourceConfirm(drive, id)
		return resc, nil
	} else {
		ResourceDelete(actor, drive, id)
		return t, err
	}
}
