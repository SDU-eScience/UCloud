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
	specificationOf func(*Spec) *orcapi.ResourceSpecification,
	create func(rpc.Actor, fndapi.BulkRequest[Spec]) ([]Resc, *util.HttpError),
	responseOf func([]Resc) Resp,
) rpc.ServerHandler[orcapi.ControlCreateRequest[Spec], Resp] {
	return func(info rpc.RequestInfo, request orcapi.ControlCreateRequest[Spec]) (Resp, *util.HttpError) {
		var empty Resp

		providerId, isProvider := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		if !isProvider {
			return empty, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		actor, job, stackInstance, err := controlResolveJobActor(info.Actor, request.JobId)
		if err != nil {
			return empty, err
		}

		stackLabels := map[string]string{
			orcapi.ResourceLabelStackInstance: stackInstance,
		}
		if value := strings.TrimSpace(job.Specification.Labels[orcapi.ResourceLabelStack]); value != "" {
			stackLabels[orcapi.ResourceLabelStack] = value
		}
		if value := strings.TrimSpace(job.Specification.Labels[orcapi.ResourceLabelStackName]); value != "" {
			stackLabels[orcapi.ResourceLabelStackName] = value
		}

		for i := range request.Items {
			spec := specificationOf(&request.Items[i])
			if !resourceSpecificationHasProduct(*spec) {
				return empty, util.HttpErr(http.StatusBadRequest, "resource does not specify a product")
			}

			if spec.Product.Provider != providerId {
				return empty, util.HttpErr(http.StatusForbidden, "forbidden")
			}

			spec.Labels = util.MapMerge(spec.Labels, stackLabels)

			if networkSpec, ok := any(request.Items[i]).(orcapi.PrivateNetworkIpSpecification); ok {
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

			if serviceSpec, ok := any(request.Items[i]).(orcapi.ServiceSpecification); ok {
				if endpoint := serviceSpec.InternalEndpoint; endpoint.Present {
					network, _, _, err := ResourceRetrieveEx[orcapi.PrivateNetwork](
						rpc.ActorSystem,
						privateNetworkType,
						ResourceParseId(endpoint.Value.PrivateNetworkId),
						orcapi.PermissionRead,
						orcapi.ResourceFlags{},
					)
					if err != nil {
						return empty, util.HttpErr(http.StatusBadRequest, "unknown network requested")
					}

					if network.Specification.Labels[orcapi.ResourceLabelStackInstance] != stackInstance {
						return empty, util.HttpErr(http.StatusBadRequest, "the internal endpoint must belong to the same stack as the job")
					}
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

func controlBrowseJobScoped(
	info rpc.RequestInfo,
	jobId string,
	flags *orcapi.ResourceFlags,
) (rpc.Actor, *util.HttpError) {
	if strings.TrimSpace(jobId) == "" {
		return info.Actor, nil
	}

	actor, _, stackInstance, err := controlResolveJobActor(info.Actor, jobId)
	if err != nil {
		return rpc.Actor{}, err
	}

	if flags.FilterLabels == nil {
		flags.FilterLabels = map[string]string{}
	}
	flags.FilterLabels[orcapi.ResourceLabelStackInstance] = stackInstance

	return actor, nil
}

func controlRetrieveJobScoped(
	info rpc.RequestInfo,
	jobId string,
	typeName string,
	id string,
) (rpc.Actor, *util.HttpError) {
	if strings.TrimSpace(jobId) == "" {
		return info.Actor, nil
	}

	actor, _, stackInstance, err := controlResolveJobActor(info.Actor, jobId)
	if err != nil {
		return rpc.Actor{}, err
	}

	_, err = controlVerifyStackMembership(actor, typeName, id, stackInstance)
	if err != nil {
		return rpc.Actor{}, err
	}

	return actor, nil
}

func controlVerifyStackMembership(actor rpc.Actor, typeName string, id string, stackInstance string) (orcapi.ResourceSpecification, *util.HttpError) {
	_, _, spec, err := ResourceRetrieveEx[any](
		actor,
		typeName,
		ResourceParseId(id),
		orcapi.PermissionRead,
		orcapi.ResourceFlags{},
	)
	if err != nil {
		return orcapi.ResourceSpecification{}, util.HttpErr(http.StatusNotFound, "not found")
	}

	if spec.Labels[orcapi.ResourceLabelStackInstance] != stackInstance {
		return orcapi.ResourceSpecification{}, util.HttpErr(http.StatusForbidden, "the resource does not belong to the stack of the job")
	}

	return spec, nil
}

var controlStackLabelKeys = []string{
	orcapi.ResourceLabelStack,
	orcapi.ResourceLabelStackController,
	orcapi.ResourceLabelStackName,
	orcapi.ResourceLabelStackInstance,
}

func controlUpdateLabelsServe[Item any](
	typeName string,
	idOf func(Item) string,
	labelsOf func(Item) map[string]string,
	update func(rpc.Actor, fndapi.BulkRequest[Item]) *util.HttpError,
) rpc.ServerHandler[orcapi.ControlMutateRequest[Item], util.Empty] {
	return func(info rpc.RequestInfo, request orcapi.ControlMutateRequest[Item]) (util.Empty, *util.HttpError) {
		if strings.TrimSpace(request.JobId) == "" {
			for _, item := range request.Items {
				err := ResourceUpdateLabels(info.Actor, typeName, idOf(item), labelsOf(item), orcapi.PermissionProvider)
				if err != nil {
					return util.Empty{}, err
				}
			}

			return util.Empty{}, nil
		}

		actor, _, stackInstance, err := controlResolveJobActor(info.Actor, request.JobId)
		if err != nil {
			return util.Empty{}, err
		}

		for _, item := range request.Items {
			spec, err := controlVerifyStackMembership(actor, typeName, idOf(item), stackInstance)
			if err != nil {
				return util.Empty{}, err
			}

			normalized, err := ResourceValidateLabels(labelsOf(item))
			if err != nil {
				return util.Empty{}, err
			}

			for _, key := range controlStackLabelKeys {
				if spec.Labels[key] != normalized[key] {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "the stack labels of a resource cannot be changed")
				}
			}
		}

		return util.Empty{}, update(actor, fndapi.BulkRequestOf(request.Items...))
	}
}

func controlMutateServe[Item any, Resp any](
	typeName string,
	idOf func(Item) string,
	mutate func(actor rpc.Actor, items []Item) (Resp, *util.HttpError),
) rpc.ServerHandler[orcapi.ControlMutateRequest[Item], Resp] {
	return func(info rpc.RequestInfo, request orcapi.ControlMutateRequest[Item]) (Resp, *util.HttpError) {
		var zero Resp

		actor, _, stackInstance, err := controlResolveJobActor(info.Actor, request.JobId)
		if err != nil {
			return zero, err
		}

		for _, item := range request.Items {
			_, err := controlVerifyStackMembership(actor, typeName, idOf(item), stackInstance)
			if err != nil {
				return zero, err
			}
		}

		return mutate(actor, request.Items)
	}
}

func controlResolveJobActor(actor rpc.Actor, jobId string) (rpc.Actor, orcapi.Job, string, *util.HttpError) {
	providerId, isProvider := strings.CutPrefix(actor.Username, fndapi.ProviderSubjectPrefix)
	if !isProvider {
		return rpc.Actor{}, orcapi.Job{}, "", util.HttpErr(http.StatusForbidden, "forbidden")
	}

	job, err := JobsRetrieve(rpc.ActorSystem, jobId, orcapi.JobFlags{})
	if err != nil {
		return rpc.Actor{}, orcapi.Job{}, "", util.HttpErr(http.StatusNotFound, "unknown job")
	}
	if job.Status.State.IsFinal() {
		return rpc.Actor{}, orcapi.Job{}, "", util.HttpErr(http.StatusBadRequest, "the job is in a final state")
	}

	if job.Specification.Product.Provider != providerId {
		return rpc.Actor{}, orcapi.Job{}, "", util.HttpErr(http.StatusForbidden, "forbidden")
	}

	stackInstance := strings.TrimSpace(job.Specification.Labels[orcapi.ResourceLabelStackInstance])
	if stackInstance == "" {
		return rpc.Actor{}, orcapi.Job{}, "", util.HttpErr(http.StatusBadRequest, "the job is not associated with a stack")
	}
	if stackInstance != job.Specification.Labels[orcapi.ResourceLabelStackInstance] {
		return rpc.Actor{}, orcapi.Job{}, "", util.HttpErr(http.StatusBadRequest, "the stack label of the job is not canonical")
	}

	jobActor, ok := rpc.LookupActor(job.Owner.CreatedBy)
	if !ok {
		return rpc.Actor{}, orcapi.Job{}, "", util.HttpErr(http.StatusForbidden, "the owner of this job is not known")
	}

	if job.Owner.Project.Present {
		if _, isMember := jobActor.Membership[rpc.ProjectId(job.Owner.Project.Value)]; !isMember {
			return rpc.Actor{}, orcapi.Job{}, "", util.HttpErr(http.StatusForbidden, "the owner of this job can no longer perform this action")
		}
		jobActor.Project.Set(rpc.ProjectId(job.Owner.Project.Value))
	}

	return jobActor, job, stackInstance, nil
}

func controlCreateIdsOf[Resc any](created []Resc, idOf func(Resc) string) fndapi.BulkResponse[fndapi.FindByStringId] {
	ids := make([]fndapi.FindByStringId, 0, len(created))
	for _, resc := range created {
		ids = append(ids, fndapi.FindByStringId{Id: idOf(resc)})
	}
	return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: ids}
}
