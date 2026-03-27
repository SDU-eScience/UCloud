package orchestrator

import (
	"net/http"

	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func ResourceCreateThroughProvider[T any](
	actor rpc.Actor,
	typeName string,
	specification orcapi.ResourceSpecification,
	extra any,
	call rpc.Call[fndapi.BulkRequest[T], fndapi.BulkResponse[fndapi.FindByStringId]],
) (T, *util.HttpError) {
	var t T

	if !resourceSpecificationHasProduct(specification) {
		return t, util.HttpErr(http.StatusBadRequest, "resource does not specify a product")
	}

	err := ResourceValidateAllocation(actor, specification.Product)
	if err != nil {
		return t, err
	}

	id, resc, err := ResourceCreate[T](actor, typeName, specification, extra)
	if err != nil {
		return t, err
	}

	resp, err := InvokeProvider(specification.Product.Provider, call, fndapi.BulkRequestOf(resc), ProviderCallOpts{
		Username: util.OptValue(actor.Username),
		Reason:   util.OptValue("Creating resource: " + typeName),
	})

	if err == nil {
		providerId := ""
		if len(resp.Responses) > 0 {
			providerId = resp.Responses[0].Id
		}

		if providerId != "" {
			ResourceSystemUpdate(typeName, id, func(r *resource, mapped T) {
				r.ProviderId.Set(providerId)
			})
		}

		ResourceConfirm(typeName, id)
		return resc, nil
	} else {
		log.Info("Provider has refused to create resource: %s. Resource is: %v %#v", err, id, extra)
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
	resc, _, specification, err := ResourceRetrieveEx[T](
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

	if !resourceSpecificationHasProduct(specification) {
		panic("ResourceDeleteThroughProvider called but with no product returned")
	}

	_, err = InvokeProvider(specification.Product.Provider, call, fndapi.BulkRequestOf(resc), ProviderCallOpts{
		Username: util.OptValue(actor.Username),
		Reason:   util.OptValue("Deleting resource: " + typeName),
	})

	if err == nil {
		ResourceDelete(actor, typeName, ResourceParseId(id))
	}
	return err
}

func ResourceUpdateLabelsThroughProvider[T any](
	actor rpc.Actor,
	typeName string,
	id string,
	labels map[string]string,
	mutatorFn func(t *T, labels map[string]string),
	call rpc.Call[fndapi.BulkRequest[T], util.Empty],
) *util.HttpError {
	resc, _, specification, err := ResourceRetrieveEx[T](
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

	originalResource := resc

	if err != nil {
		return err
	}

	if !resourceSpecificationHasProduct(specification) {
		panic("ResourceUpdateLabelsThroughProvider called but with no product returned")
	}

	normalized, err := ResourceValidateLabels(labels)
	if err != nil {
		return err
	}

	mutatorFn(&resc, normalized)

	_, err = InvokeProvider(specification.Product.Provider, call, fndapi.BulkRequestOf(resc), ProviderCallOpts{
		Username: util.OptValue(actor.Username),
		Reason:   util.OptValue("User initiated label update"),
	})

	if err != nil && err.StatusCode != http.StatusNotFound {
		return err
	} else {
		err = ResourceUpdateLabels(actor, typeName, id, normalized, orcapi.PermissionEdit)
		if err != nil {
			_, _ = InvokeProvider(specification.Product.Provider, call, fndapi.BulkRequestOf(originalResource), ProviderCallOpts{
				Username: util.OptValue(actor.Username),
				Reason:   util.OptValue("Rolling back label changes"),
			})
			return err
		} else {
			return nil
		}
	}
}
