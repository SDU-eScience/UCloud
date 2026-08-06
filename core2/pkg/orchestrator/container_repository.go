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

const containerRepositoryType = "container_repository"

var containerRepositoryNameRegex = regexp.MustCompile(`^[a-z0-9]+(?:-[a-z0-9]+)*$`)

var containerRepositoriesByName struct {
	Mu    sync.RWMutex
	Names map[string]ResourceId
}

func initContainerRepositories() {
	InitResourceType(
		containerRepositoryType,
		0,
		containerRepositoryLoad,
		containerRepositoryPersist,
		containerRepositoryTransform,
		nil,
	)

	containerRepositoriesFillIndex()
	ResourceAddIndexer(
		containerRepositoryType,
		func(r *resource) ResourceIndexer {
			return &containerRepositoryNameIndexer{resource: r}
		},
	)

	orcapi.ContainerRepositoriesCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ContainerRepositorySpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		created, err := ContainerRepositoryCreate(info.Actor, request)
		if err != nil {
			return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
		}

		responses := make([]fndapi.FindByStringId, 0, len(created))
		for _, repository := range created {
			responses = append(responses, fndapi.FindByStringId{Id: repository.Id})
		}
		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})

	orcapi.ContainerRepositoriesDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		return ContainerRepositoryDelete(info.Actor, request)
	})

	orcapi.ContainerRepositoriesBrowse.Handler(func(info rpc.RequestInfo, request orcapi.ContainerRepositoriesBrowseRequest) (fndapi.PageV2[orcapi.ContainerRepository], *util.HttpError) {
		return ContainerRepositoryBrowse(info.Actor, request), nil
	})

	orcapi.ContainerRepositoriesRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.ContainerRepositoriesRetrieveRequest) (orcapi.ContainerRepository, *util.HttpError) {
		return ResourceRetrieve[orcapi.ContainerRepository](info.Actor, containerRepositoryType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.ContainerRepositoriesUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.UpdatedAcl]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		for _, item := range request.Items {
			if err := ResourceUpdateAcl(info.Actor, containerRepositoryType, item); err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}
		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.ContainerRepositoriesUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ContainerRepositoriesUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		return util.Empty{}, ContainerRepositoryUpdateLabels(info.Actor, request)
	})

	orcapi.ContainerRepositoriesRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.FSSupport], *util.HttpError) {
		return ContainerRepositoryRetrieveProducts(), nil
	})

	orcapi.ContainerRepositoriesBrowseImages.Handler(func(info rpc.RequestInfo, request orcapi.ContainerRepositoriesBrowseImagesRequest) (fndapi.PageV2[orcapi.ContainerRepositoryImage], *util.HttpError) {
		return ContainerRepositoryBrowseImages(info.Actor, request)
	})

	orcapi.ContainerRepositoriesDeleteImage.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ContainerRepositoriesDeleteImageRequest]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		return ContainerRepositoryDeleteImage(info.Actor, request)
	})

	orcapi.ContainerRepositoriesControlRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.ContainerRepositoriesControlRetrieveRequest) (orcapi.ContainerRepository, *util.HttpError) {
		return ResourceRetrieve[orcapi.ContainerRepository](info.Actor, containerRepositoryType, ResourceParseId(request.Id), request.ResourceFlags)
	})

	orcapi.ContainerRepositoriesControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.ContainerRepositoriesControlBrowseRequest) (fndapi.PageV2[orcapi.ContainerRepository], *util.HttpError) {
		return ResourceBrowse(
			info.Actor,
			containerRepositoryType,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.ContainerRepository) bool { return true },
			nil,
		), nil
	})

	orcapi.ContainerRepositoriesControlRegister.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderRegisteredResource[orcapi.ContainerRepositorySpecification]]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		providerId, _ := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		responses := make([]fndapi.FindByStringId, 0, len(request.Items))

		for _, reqItem := range request.Items {
			if reqItem.Spec.Product.Provider != providerId {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusForbidden, "forbidden")
			}

			reqItem.Spec.Name = strings.TrimSpace(reqItem.Spec.Name)
			if err := containerRepositoryValidateSpecification(reqItem.Spec); err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			if !containerRepositoryReserve(reqItem.Spec.Product.Provider, reqItem.Spec.Name) {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusConflict, "container repository name already exists")
			}

			var flags resourceCreateFlags
			if reqItem.ProjectAllRead {
				flags |= resourceCreateAllRead
			}
			if reqItem.ProjectAllWrite {
				flags |= resourceCreateAllWrite
			}

			id, _, err := ResourceCreateEx[orcapi.ContainerRepository](
				containerRepositoryType,
				orcapi.ResourceOwner{
					CreatedBy: reqItem.CreatedBy.GetOrDefault("_ucloud"),
					Project:   util.OptStringIfNotEmpty(reqItem.Project.Value),
				},
				nil,
				reqItem.Spec.ResourceSpecification,
				reqItem.ProviderGeneratedId,
				&internalContainerRepository{Name: reqItem.Spec.Name},
				flags,
			)
			if err != nil {
				containerRepositoryReleaseReservation(reqItem.Spec.Product.Provider, reqItem.Spec.Name)
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			ResourceConfirm(containerRepositoryType, id)
			responses = append(responses, fndapi.FindByStringId{Id: fmt.Sprint(id)})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})

	orcapi.ContainerRepositoriesControlUpdateLabels.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ContainerRepositoriesUpdateLabelsRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			if err := ResourceUpdateLabels(info.Actor, containerRepositoryType, reqItem.Id, reqItem.Labels, orcapi.PermissionProvider); err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})
}

func ContainerRepositoryCreate(actor rpc.Actor, request fndapi.BulkRequest[orcapi.ContainerRepositorySpecification]) ([]orcapi.ContainerRepository, *util.HttpError) {
	if !actor.Project.Present || !actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin) {
		return nil, util.HttpErr(http.StatusForbidden, "you need project administrator privileges to do this operation")
	}

	created := make([]orcapi.ContainerRepository, 0, len(request.Items))
	for _, item := range request.Items {
		item.Name = strings.TrimSpace(item.Name)
		if err := containerRepositoryValidateSpecification(item); err != nil {
			return nil, err
		}

		if !featureSupported(driveType, item.Product, driveContainerRepositories) {
			return nil, featureNotSupportedError
		}

		if !containerRepositoryReserve(item.Product.Provider, item.Name) {
			return nil, util.HttpErr(http.StatusConflict, "container repository name already exists")
		}

		repository, err := ResourceCreateThroughProvider(
			actor,
			containerRepositoryType,
			item.ResourceSpecification,
			&internalContainerRepository{Name: item.Name},
			orcapi.ContainerRepositoriesProviderCreate,
		)
		if err != nil {
			containerRepositoryReleaseReservation(item.Product.Provider, item.Name)
			return nil, err
		}

		created = append(created, repository)
	}

	return created, nil
}

func ContainerRepositoryBrowse(actor rpc.Actor, request orcapi.ContainerRepositoriesBrowseRequest) fndapi.PageV2[orcapi.ContainerRepository] {
	sortByFn := ResourceDefaultComparator(func(item orcapi.ContainerRepository) orcapi.Resource {
		return item.Resource
	}, request.ResourceFlags)

	switch request.SortBy.GetOrDefault("") {
	case "", "name":
		sortByFn = func(a orcapi.ContainerRepository, b orcapi.ContainerRepository) int {
			return strings.Compare(a.Specification.Name, b.Specification.Name)
		}
	}

	return ResourceBrowse(
		actor,
		containerRepositoryType,
		request.Next,
		request.ItemsPerPage,
		request.ResourceFlags,
		func(item orcapi.ContainerRepository) bool {
			return true
		},
		sortByFn,
	)
}

func ContainerRepositoryDelete(actor rpc.Actor, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	responses := make([]util.Empty, 0, len(request.Items))
	for _, item := range request.Items {
		if err := ResourceDeleteThroughProvider[orcapi.ContainerRepository](actor, containerRepositoryType, item.Id, orcapi.ContainerRepositoriesProviderDelete); err != nil {
			return fndapi.BulkResponse[util.Empty]{}, err
		}
		responses = append(responses, util.Empty{})
	}
	return fndapi.BulkResponse[util.Empty]{Responses: responses}, nil
}

func ContainerRepositoryUpdateLabels(actor rpc.Actor, request fndapi.BulkRequest[orcapi.ContainerRepositoriesUpdateLabelsRequest]) *util.HttpError {
	for _, reqItem := range request.Items {
		err := ResourceUpdateLabelsThroughProvider[orcapi.ContainerRepository](
			actor,
			containerRepositoryType,
			reqItem.Id,
			reqItem.Labels,
			func(repository *orcapi.ContainerRepository, labels map[string]string) {
				repository.Specification.Labels = labels
			},
			orcapi.ContainerRepositoriesProviderOnUpdatedLabels,
		)
		if err != nil {
			return err
		}
	}
	return nil
}

func ContainerRepositoryRetrieveProducts() orcapi.SupportByProvider[orcapi.FSSupport] {
	result := SupportRetrieveProducts[orcapi.FSSupport](driveType)
	for provider, products := range result.ProductsByProvider {
		filtered := make([]orcapi.ResolvedSupport[orcapi.FSSupport], 0, len(products))
		for _, product := range products {
			if slices.Contains(product.Features, string(driveContainerRepositories)) {
				filtered = append(filtered, product)
			}
		}
		result.ProductsByProvider[provider] = util.NonNilSlice(filtered)
	}
	return result
}

func ContainerRepositoryBrowseImages(actor rpc.Actor, request orcapi.ContainerRepositoriesBrowseImagesRequest) (fndapi.PageV2[orcapi.ContainerRepositoryImage], *util.HttpError) {
	repository, _, _, err := ResourceRetrieveEx[orcapi.ContainerRepository](
		actor,
		containerRepositoryType,
		ResourceParseId(request.RepositoryId),
		orcapi.PermissionRead,
		orcapi.ResourceFlagsIncludeAll(),
	)
	if err != nil {
		return fndapi.PageV2[orcapi.ContainerRepositoryImage]{}, err
	}

	return InvokeProvider(
		repository.Specification.Product.Provider,
		orcapi.ContainerRepositoriesProviderBrowseImages,
		orcapi.ContainerRepositoriesProviderBrowseImagesRequest{
			ResolvedRepository: repository,
			Repository:         request.Repository,
			Tag:                request.Tag,
			ItemsPerPage:       request.ItemsPerPage,
			Next:               request.Next,
		},
		ProviderCallOpts{
			Username: util.OptValue(actor.Username),
			Reason:   util.OptValue("browse container repository images"),
		},
	)
}

func ContainerRepositoryDeleteImage(actor rpc.Actor, request fndapi.BulkRequest[orcapi.ContainerRepositoriesDeleteImageRequest]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
	result := fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, 0, len(request.Items))}
	for _, item := range request.Items {
		repository, _, _, err := ResourceRetrieveEx[orcapi.ContainerRepository](
			actor,
			containerRepositoryType,
			ResourceParseId(item.RepositoryId),
			orcapi.PermissionEdit,
			orcapi.ResourceFlagsIncludeAll(),
		)
		if err != nil {
			return fndapi.BulkResponse[util.Empty]{}, err
		}

		_, err = InvokeProvider(
			repository.Specification.Product.Provider,
			orcapi.ContainerRepositoriesProviderDeleteImage,
			fndapi.BulkRequestOf(orcapi.ContainerRepositoriesProviderDeleteImageRequest{
				ResolvedRepository: repository,
				Repository:         item.Repository,
				Tag:                item.Tag,
			}),
			ProviderCallOpts{
				Username: util.OptValue(actor.Username),
				Reason:   util.OptValue("delete container repository image"),
			},
		)
		if err != nil {
			return fndapi.BulkResponse[util.Empty]{}, err
		}
		result.Responses = append(result.Responses, util.Empty{})
	}
	return result, nil
}

func containerRepositoryValidateSpecification(spec orcapi.ContainerRepositorySpecification) *util.HttpError {
	if len(spec.Name) == 0 || len(spec.Name) > 63 || !containerRepositoryNameRegex.MatchString(spec.Name) {
		return util.HttpErr(http.StatusBadRequest, "invalid container repository name")
	}
	return nil
}

func containerRepositoryKey(provider string, name string) string {
	return provider + "\x1f" + name
}

func containerRepositoryReserve(provider string, name string) bool {
	key := containerRepositoryKey(provider, name)
	containerRepositoriesByName.Mu.Lock()
	_, exists := containerRepositoriesByName.Names[key]
	if !exists {
		containerRepositoriesByName.Names[key] = 0
	}
	containerRepositoriesByName.Mu.Unlock()
	return !exists
}

func containerRepositoryReleaseReservation(provider string, name string) {
	key := containerRepositoryKey(provider, name)
	containerRepositoriesByName.Mu.Lock()
	if id, exists := containerRepositoriesByName.Names[key]; exists && id == 0 {
		delete(containerRepositoriesByName.Names, key)
	}
	containerRepositoriesByName.Mu.Unlock()
}

func containerRepositoriesFillIndex() {
	containerRepositoriesByName.Names = map[string]ResourceId{}
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

		containerRepositoriesByName.Mu.Lock()
		for _, row := range rows {
			containerRepositoriesByName.Names[containerRepositoryKey(row.Provider, row.Name)] = ResourceId(row.Resource)
		}
		containerRepositoriesByName.Mu.Unlock()
	})
}

type containerRepositoryNameIndexer struct {
	resource *resource
}

func (i *containerRepositoryNameIndexer) Begin() {
	containerRepositoriesByName.Mu.Lock()
}

func (i *containerRepositoryNameIndexer) Add() {
	repository := i.resource.Extra.(*internalContainerRepository)
	containerRepositoriesByName.Names[containerRepositoryKey(i.resource.BaseSpec.Product.Provider, repository.Name)] = i.resource.Id
}

func (i *containerRepositoryNameIndexer) Remove() {
	repository := i.resource.Extra.(*internalContainerRepository)
	key := containerRepositoryKey(i.resource.BaseSpec.Product.Provider, repository.Name)
	if id, exists := containerRepositoriesByName.Names[key]; exists && id == i.resource.Id {
		delete(containerRepositoriesByName.Names, key)
	}
}

func (i *containerRepositoryNameIndexer) Commit() {
	containerRepositoriesByName.Mu.Unlock()
}

type internalContainerRepository struct {
	Name string
}

func containerRepositoryLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
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
		resources[ResourceId(row.Resource)].Extra = &internalContainerRepository{Name: row.Name}
	}
}

func containerRepositoryPersist(b *db.Batch, r *resource) {
	if r.MarkedForDeletion {
		db.BatchExec(
			b,
			`delete from file_orchestrator.container_repositories where resource = :resource`,
			db.Params{"resource": r.Id},
		)
		return
	}

	repository := r.Extra.(*internalContainerRepository)
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
			"name":     repository.Name,
			"provider": r.BaseSpec.Product.Provider,
		},
	)
}

func containerRepositoryTransform(
	r orcapi.Resource,
	specification orcapi.ResourceSpecification,
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	repository := extra.(*internalContainerRepository)
	result := orcapi.ContainerRepository{
		Resource: r,
		Specification: orcapi.ContainerRepositorySpecification{
			Name:                  repository.Name,
			ResourceSpecification: specification,
		},
		Status: orcapi.ContainerRepositoryStatus{},
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
