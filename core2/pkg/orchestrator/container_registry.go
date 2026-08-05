package orchestrator

import (
	"fmt"
	"net/http"
	"regexp"
	"slices"
	"strings"
	"sync"

	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const containerRegistryType = "container_repository"

var containerRegistryNameRegex = regexp.MustCompile(`^[a-z0-9]+(?:-[a-z0-9]+)*$`)

var containerRegistriesByName struct {
	Mu    sync.RWMutex
	Names map[string]ResourceId
}

func initContainerRegistries() {
	InitResourceType(
		containerRegistryType,
		0,
		containerRegistryLoad,
		containerRegistryPersist,
		containerRegistryTransform,
		nil,
	)

	containerRegistriesFillIndex()
	ResourceAddIndexer(
		containerRegistryType,
		func(r *resource) ResourceIndexer {
			return &containerRegistryNameIndexer{resource: r}
		},
	)

	orcapi.ContainerRegistriesCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ContainerRegistrySpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		created, err := ContainerRegistryCreate(info.Actor, request)
		if err != nil {
			return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
		}

		responses := make([]fndapi.FindByStringId, 0, len(created))
		for _, registry := range created {
			responses = append(responses, fndapi.FindByStringId{Id: registry.Id})
		}
		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})

	orcapi.ContainerRegistriesDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		return ContainerRegistryDelete(info.Actor, request)
	})

	orcapi.ContainerRegistriesRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.ContainerRegistriesRetrieveRequest) (orcapi.ContainerRegistry, *util.HttpError) {
		return ResourceRetrieve[orcapi.ContainerRegistry](info.Actor, containerRegistryType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.ContainerRegistriesUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.UpdatedAcl]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			if err := ResourceUpdateAcl(info.Actor, containerRegistryType, item); err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}
		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.ContainerRegistriesUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ContainerRegistriesUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		return util.Empty{}, ContainerRegistryUpdateLabels(info.Actor, request)
	})

	orcapi.ContainerRegistriesRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.FSSupport], *util.HttpError) {
		return ContainerRegistryRetrieveProducts(), nil
	})

	orcapi.ContainerRegistriesControlRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.ContainerRegistriesControlRetrieveRequest) (orcapi.ContainerRegistry, *util.HttpError) {
		return ResourceRetrieve[orcapi.ContainerRegistry](info.Actor, containerRegistryType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.ContainerRegistriesControlRegister.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderRegisteredResource[orcapi.ContainerRegistrySpecification]]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		providerId, _ := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		responses := make([]fndapi.FindByStringId, 0, len(request.Items))

		for _, reqItem := range request.Items {
			if reqItem.Spec.Product.Provider != providerId {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusForbidden, "forbidden")
			}

			reqItem.Spec.Name = strings.TrimSpace(reqItem.Spec.Name)
			if err := containerRegistryValidateSpecification(reqItem.Spec); err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			if !containerRegistryReserve(reqItem.Spec.Product.Provider, reqItem.Spec.Name) {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusConflict, "container registry name already exists")
			}

			var flags resourceCreateFlags
			if reqItem.ProjectAllRead {
				flags |= resourceCreateAllRead
			}
			if reqItem.ProjectAllWrite {
				flags |= resourceCreateAllWrite
			}

			id, _, err := ResourceCreateEx[orcapi.ContainerRegistry](
				containerRegistryType,
				orcapi.ResourceOwner{
					CreatedBy: reqItem.CreatedBy.GetOrDefault("_ucloud"),
					Project:   util.OptStringIfNotEmpty(reqItem.Project.Value),
				},
				nil,
				reqItem.Spec.ResourceSpecification,
				reqItem.ProviderGeneratedId,
				&internalContainerRegistry{Name: reqItem.Spec.Name},
				flags,
			)
			if err != nil {
				containerRegistryReleaseReservation(reqItem.Spec.Product.Provider, reqItem.Spec.Name)
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			ResourceConfirm(containerRegistryType, id)
			responses = append(responses, fndapi.FindByStringId{Id: fmt.Sprint(id)})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})

	orcapi.ContainerRegistriesControlUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ContainerRegistriesUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			if err := ResourceUpdateLabels(info.Actor, containerRegistryType, reqItem.Id, reqItem.Labels, orcapi.PermissionProvider); err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})
}

func ContainerRegistryCreate(actor rpc.Actor, request fndapi.BulkRequest[orcapi.ContainerRegistrySpecification]) ([]orcapi.ContainerRegistry, *util.HttpError) {
	if !actor.Project.Present || !actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin) {
		return nil, util.HttpErr(http.StatusForbidden, "you need project administrator privileges to do this operation")
	}

	created := make([]orcapi.ContainerRegistry, 0, len(request.Items))
	for _, item := range request.Items {
		item.Name = strings.TrimSpace(item.Name)
		if err := containerRegistryValidateSpecification(item); err != nil {
			return nil, err
		}

		if !featureSupported(driveType, item.Product, driveContainerRegistries) {
			return nil, featureNotSupportedError
		}

		if !containerRegistryReserve(item.Product.Provider, item.Name) {
			return nil, util.HttpErr(http.StatusConflict, "container registry name already exists")
		}

		registry, err := ResourceCreateThroughProvider(
			actor,
			containerRegistryType,
			item.ResourceSpecification,
			&internalContainerRegistry{Name: item.Name},
			orcapi.ContainerRegistriesProviderCreate,
		)
		if err != nil {
			containerRegistryReleaseReservation(item.Product.Provider, item.Name)
			return nil, err
		}

		created = append(created, registry)
	}

	return created, nil
}

func ContainerRegistryDelete(actor rpc.Actor, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	responses := make([]util.Empty, 0, len(request.Items))
	for _, item := range request.Items {
		if err := ResourceDeleteThroughProvider[orcapi.ContainerRegistry](actor, containerRegistryType, item.Id, orcapi.ContainerRegistriesProviderDelete); err != nil {
			return fndapi.BulkResponse[util.Empty]{}, err
		}
		responses = append(responses, util.Empty{})
	}
	return fndapi.BulkResponse[util.Empty]{Responses: responses}, nil
}

func ContainerRegistryUpdateLabels(actor rpc.Actor, request fndapi.BulkRequest[orcapi.ContainerRegistriesUpdateLabelsRequest]) *util.HttpError {
	for _, reqItem := range request.Items {
		err := ResourceUpdateLabelsThroughProvider[orcapi.ContainerRegistry](
			actor,
			containerRegistryType,
			reqItem.Id,
			reqItem.Labels,
			func(registry *orcapi.ContainerRegistry, labels map[string]string) {
				registry.Specification.Labels = labels
			},
			orcapi.ContainerRegistriesProviderOnUpdatedLabels,
		)
		if err != nil {
			return err
		}
	}
	return nil
}

func ContainerRegistryRetrieveProducts() orcapi.SupportByProvider[orcapi.FSSupport] {
	result := SupportRetrieveProducts[orcapi.FSSupport](driveType)
	for provider, products := range result.ProductsByProvider {
		filtered := make([]orcapi.ResolvedSupport[orcapi.FSSupport], 0, len(products))
		for _, product := range products {
			if slices.Contains(product.Features, string(driveContainerRegistries)) {
				filtered = append(filtered, product)
			}
		}
		result.ProductsByProvider[provider] = util.NonNilSlice(filtered)
	}
	return result
}

func containerRegistryValidateSpecification(spec orcapi.ContainerRegistrySpecification) *util.HttpError {
	if len(spec.Name) == 0 || len(spec.Name) > 63 || !containerRegistryNameRegex.MatchString(spec.Name) {
		return util.HttpErr(http.StatusBadRequest, "invalid container registry name")
	}
	return nil
}

func containerRegistryKey(provider string, name string) string {
	return provider + "\x1f" + name
}

func containerRegistryReserve(provider string, name string) bool {
	key := containerRegistryKey(provider, name)
	containerRegistriesByName.Mu.Lock()
	_, exists := containerRegistriesByName.Names[key]
	if !exists {
		containerRegistriesByName.Names[key] = 0
	}
	containerRegistriesByName.Mu.Unlock()
	return !exists
}

func containerRegistryReleaseReservation(provider string, name string) {
	key := containerRegistryKey(provider, name)
	containerRegistriesByName.Mu.Lock()
	if id, exists := containerRegistriesByName.Names[key]; exists && id == 0 {
		delete(containerRegistriesByName.Names, key)
	}
	containerRegistriesByName.Mu.Unlock()
}

func containerRegistriesFillIndex() {
	containerRegistriesByName.Names = map[string]ResourceId{}
	if resourceGlobals.Testing.Enabled {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		rows := db.Select[struct {
			Resource int64
			Name     string
			Provider string
		}](
			tx,
			`select resource, name, provider from file_orchestrator.container_repositories`,
			db.Params{},
		)

		containerRegistriesByName.Mu.Lock()
		for _, row := range rows {
			containerRegistriesByName.Names[containerRegistryKey(row.Provider, row.Name)] = ResourceId(row.Resource)
		}
		containerRegistriesByName.Mu.Unlock()
	})
}

type containerRegistryNameIndexer struct {
	resource *resource
}

func (i *containerRegistryNameIndexer) Begin() {
	containerRegistriesByName.Mu.Lock()
}

func (i *containerRegistryNameIndexer) Add() {
	registry := i.resource.Extra.(*internalContainerRegistry)
	containerRegistriesByName.Names[containerRegistryKey(i.resource.BaseSpec.Product.Provider, registry.Name)] = i.resource.Id
}

func (i *containerRegistryNameIndexer) Remove() {
	registry := i.resource.Extra.(*internalContainerRegistry)
	key := containerRegistryKey(i.resource.BaseSpec.Product.Provider, registry.Name)
	if id, exists := containerRegistriesByName.Names[key]; exists && id == i.resource.Id {
		delete(containerRegistriesByName.Names, key)
	}
}

func (i *containerRegistryNameIndexer) Commit() {
	containerRegistriesByName.Mu.Unlock()
}

type internalContainerRegistry struct {
	Name string
}

func containerRegistryLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource int64
		Name     string
	}](
		tx,
		`
			select resource, name
			from file_orchestrator.container_repositories
			where resource = some(:ids::int8[])
		`,
		db.Params{"ids": ids},
	)

	for _, row := range rows {
		resources[ResourceId(row.Resource)].Extra = &internalContainerRegistry{Name: row.Name}
	}
}

func containerRegistryPersist(b *db.Batch, r *resource) {
	if r.MarkedForDeletion {
		db.BatchExec(
			b,
			`delete from file_orchestrator.container_repositories where resource = :resource`,
			db.Params{"resource": r.Id},
		)
		return
	}

	registry := r.Extra.(*internalContainerRegistry)
	db.BatchExec(
		b,
		`
			insert into file_orchestrator.container_repositories(resource, name, provider)
			values (:resource, :name, :provider)
			on conflict (resource) do update set
				name = excluded.name,
				provider = excluded.provider
			`,
		db.Params{
			"resource": r.Id,
			"name":     registry.Name,
			"provider": r.BaseSpec.Product.Provider,
		},
	)
}

func containerRegistryTransform(
	r orcapi.Resource,
	specification orcapi.ResourceSpecification,
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	registry := extra.(*internalContainerRegistry)
	result := orcapi.ContainerRegistry{
		Resource: r,
		Specification: orcapi.ContainerRegistrySpecification{
			Name:                  registry.Name,
			ResourceSpecification: specification,
		},
		Status: orcapi.ContainerRegistryStatus{},
	}

	if (flags.IncludeProduct || flags.IncludeSupport) && resourceSpecificationHasProduct(specification) {
		support, _ := SupportByProduct[orcapi.FSSupport](driveType, specification.Product)
		result.Status.ResourceStatus = orcapi.ResourceStatus[orcapi.FSSupport]{
			ResolvedSupport: util.OptValue(support.ToApi()),
			ResolvedProduct: util.OptValue(support.Product),
		}
	}

	return result
}
