package orchestrator

import (
	"net/http"
	"strings"

	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func controlCreateServe[Spec any, Resc any, Resp any](
	specificationOf func(Spec) orcapi.ResourceSpecification,
	create func(rpc.Actor, fndapi.BulkRequest[Spec]) ([]Resc, *util.HttpError),
	responseOf func([]Resc) Resp,
) rpc.ServerHandler[orcapi.ControlCreateRequest[Spec], Resp] {
	return func(info rpc.RequestInfo, request orcapi.ControlCreateRequest[Spec]) (Resp, *util.HttpError) {
		var empty Resp

		providerId, isProvider := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		if !isProvider {
			return empty, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		job, err := JobsRetrieve(rpc.ActorSystem, request.JobId, orcapi.JobFlags{})
		if err != nil {
			return empty, util.HttpErr(http.StatusNotFound, "unknown job")
		}

		if job.Status.State.IsFinal() {
			return empty, util.HttpErr(http.StatusBadRequest, "the job is in a final state")
		}

		if job.Specification.Product.Provider != providerId {
			return empty, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		stackInstance := strings.TrimSpace(job.Specification.Labels[orcapi.ResourceLabelStackInstance])
		if stackInstance == "" {
			return empty, util.HttpErr(http.StatusBadRequest, "the job is not associated with a stack")
		}
		if stackInstance != job.Specification.Labels[orcapi.ResourceLabelStackInstance] {
			return empty, util.HttpErr(http.StatusBadRequest, "the stack label of the job is not canonical")
		}

		actor, ok := rpc.LookupActor(job.Owner.CreatedBy)
		if !ok {
			return empty, util.HttpErr(http.StatusForbidden, "the owner of this job is not known")
		}

		if job.Owner.Project.Present {
			if _, isMember := actor.Membership[rpc.ProjectId(job.Owner.Project.Value)]; !isMember {
				return empty, util.HttpErr(http.StatusForbidden, "the owner of this job can no longer perform this action")
			}
			actor.Project.Set(rpc.ProjectId(job.Owner.Project.Value))
		}

		for _, item := range request.Items {
			spec := specificationOf(item)
			if !resourceSpecificationHasProduct(spec) {
				return empty, util.HttpErr(http.StatusBadRequest, "resource does not specify a product")
			}

			if spec.Product.Provider != providerId {
				return empty, util.HttpErr(http.StatusForbidden, "forbidden")
			}

			if spec.Labels[orcapi.ResourceLabelStackInstance] != stackInstance {
				return empty, util.HttpErr(http.StatusBadRequest, "all resources must belong to the stack of the job")
			}

			if networkSpec, ok := any(item).(orcapi.PrivateNetworkIpSpecification); ok {
				network, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
					rpc.ActorSystem,
					privateNetworkType,
					ResourceParseId(networkSpec.Network),
					orcapi.PermissionRead,
					orcapi.ResourceFlags{},
				)
				if err != nil {
					return empty, util.HttpErr(http.StatusBadRequest, "unknown network requested")
				}

				if network.Specification.Labels[orcapi.ResourceLabelStackInstance] != stackInstance {
					return empty, util.HttpErr(http.StatusBadRequest, "the parent network must belong to the same stack as the job")
				}
			}
		}

		created, err := create(actor, fndapi.BulkRequestOf(request.Items...))
		if err != nil {
			return empty, err
		}

		return responseOf(created), nil
	}
}

func controlCreateIdsOf[Resc any](created []Resc, idOf func(Resc) string) fndapi.BulkResponse[fndapi.FindByStringId] {
	ids := make([]fndapi.FindByStringId, 0, len(created))
	for _, resc := range created {
		ids = append(ids, fndapi.FindByStringId{Id: idOf(resc)})
	}
	return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: ids}
}
