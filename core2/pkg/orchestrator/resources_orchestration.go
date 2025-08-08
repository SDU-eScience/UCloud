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
			ResourceSystemUpdate(typeName, id, func(r *resource, mapped orcapi.Drive) {
				r.ProviderId.Set(providerId)
			})
		}

		ResourceConfirm(typeName, id)
		return resc, nil
	} else {
		ResourceDelete(actor, typeName, id)
		return t, err
	}
}

func ResourceDeleteThroughProvider[T any](
	actor rpc.Actor,
	typeName string,
	id string,
	call rpc.Call[fndapi.BulkRequest[T], fndapi.BulkResponse[util.Empty]],
) *util.HttpError {
	resc, _, product, err := ResourceRetrieveEx[T](
		actor,
		typeName,
		ResourceParseId(id),
		orcapi.PermissionEdit,
		orcapi.ResourceFlags{
			IncludeOthers:  true,
			IncludeUpdates: true,
			IncludeSupport: true,
			IncludeProduct: true,
		},
	)

	if err != nil {
		return err
	}

	if !product.Present {
		panic("ResourceDeleteThroughProvider called but with no product returned")
	}

	_, err = InvokeProvider(product.Value.Provider, call, fndapi.BulkRequestOf(resc), ProviderCallOpts{
		Username: util.OptValue(actor.Username),
		Reason:   util.OptValue("Deleting resource: " + typeName),
	})

	ResourceDelete(actor, typeName, ResourceParseId(id))
	return err
}
