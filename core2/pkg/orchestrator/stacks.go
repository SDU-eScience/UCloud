package orchestrator

import (
	"database/sql"
	"net/http"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const stackType = "stack"

var stacksMutationMu sync.Mutex

func stacksCloneAcl(acl []orcapi.ResourceAclEntry) []orcapi.ResourceAclEntry {
	result := slices.Clone(acl)
	for i := range result {
		result[i].Permissions = slices.Clone(result[i].Permissions)
	}
	return result
}

func stacksResourceIds(typeName string, owner orcapi.ResourceOwner, id string) []ResourceId {
	reference := owner.Project.GetOrDefault(owner.CreatedBy)
	index := resourceGetAndLoadIndex(typeName, reference)
	label := orcapi.ResourceLabelStackEntity
	if typeName == stackType {
		label = orcapi.ResourceLabelStackInstance
	}
	index.Mu.RLock()
	ids, _ := resourceFilterByIndexedLabelsLocked(index, reference, index.ByOwner[reference], map[string]string{
		label: id,
	})
	ids = slices.Clone(ids)
	index.Mu.RUnlock()
	return ids
}

func stacksFindEntity(actor rpc.Actor, id string) (orcapi.Resource, bool) {
	owner := orcapi.ResourceOwner{
		CreatedBy: actor.Username,
		Project:   util.OptMap(actor.Project, func(value rpc.ProjectId) string { return string(value) }),
	}
	for _, resourceId := range stacksResourceIds(stackType, owner, id) {
		_, entity, _, err := ResourceRetrieveEx[orcapi.Stack](rpc.ActorSystem, stackType, resourceId, orcapi.PermissionRead, orcapi.ResourceFlags{IncludeOthers: true})
		if err == nil {
			return entity, true
		}
	}
	return orcapi.Resource{}, false
}

func StacksCreate(actor rpc.Actor, id string, name string, stateFolder string) *util.HttpError {
	if strings.TrimSpace(id) == "" || strings.TrimSpace(name) == "" {
		return util.HttpErr(http.StatusBadRequest, "stack id and type are required")
	}
	if actor.Project.Present {
		if _, member := actor.Membership[actor.Project.Value]; !member {
			return util.HttpErr(http.StatusForbidden, "project membership is required")
		}
	}
	stacksMutationMu.Lock()
	defer stacksMutationMu.Unlock()
	if _, exists := stacksFindEntity(actor, id); exists {
		return util.HttpErr(http.StatusConflict, "stack already exists")
	}
	var stateDrive util.Option[orcapi.Drive]
	if driveId, valid := orcapi.DriveIdFromUCloudPath(stateFolder); valid && stateFolder == "/"+driveId {
		drive, err := ResourceRetrieve[orcapi.Drive](actor, driveType, ResourceParseId(driveId), orcapi.ResourceFlags{})
		if err != nil {
			return err
		}
		project := util.OptMap(actor.Project, func(value rpc.ProjectId) string { return string(value) })
		if drive.Owner.CreatedBy != actor.Username || drive.Owner.Project != project || drive.Specification.Labels[orcapi.ResourceLabelStackInstance] != id {
			return util.HttpErr(http.StatusBadRequest, "state drive must belong to the stack creator")
		}
		stateDrive.Set(drive)
	}
	resourceId, _, err := ResourceCreate[orcapi.Stack](actor, stackType, orcapi.ResourceSpecification{
		Labels: map[string]string{
			orcapi.ResourceLabelStackInstance:    id,
			orcapi.ResourceLabelStackName:        name,
			orcapi.ResourceLabelStackStateFolder: stateFolder,
		},
	}, nil)
	if err != nil {
		return err
	}
	ResourceConfirm(stackType, resourceId)
	if stateDrive.Present {
		labels := util.MapMerge(stateDrive.Value.Specification.Labels, map[string]string{
			orcapi.ResourceLabelStackEntity: strconv.FormatUint(uint64(resourceId), 10),
		})
		return ResourceUpdateLabels(actor, driveType, stateDrive.Value.Id, labels, orcapi.PermissionAdmin)
	}
	return nil
}

func stacksPrepareResource(actor rpc.Actor, specification *orcapi.ResourceSpecification) *util.HttpError {
	id := strings.TrimSpace(specification.Labels[orcapi.ResourceLabelStackInstance])
	if id == "" {
		return nil
	}
	entity, exists := stacksFindEntity(actor, id)
	if !exists {
		return nil
	}
	_, stack, _, err := ResourceRetrieveEx[orcapi.Stack](actor, stackType, ResourceParseId(entity.Id), orcapi.PermissionEdit, orcapi.ResourceFlags{IncludeOthers: true})
	if err != nil {
		return err
	}
	stack.Permissions.Value.Others = stacksCloneAcl(stack.Permissions.Value.Others)
	specification.StackResource = &stack
	specification.Labels = util.MapMerge(specification.Labels, map[string]string{
		orcapi.ResourceLabelStackEntity: entity.Id,
	})
	return nil
}

func stacksApplyAcl(entity orcapi.Resource) *util.HttpError {
	var result *util.HttpError
	for _, typeName := range []string{jobType, driveType, licenseType, publicIpType, ingressType, privateNetworkType, privateNetworkIpType, serviceType, containerRepositoryType} {
		for _, resourceId := range stacksResourceIds(typeName, entity.Owner, entity.Id) {
			_, child, _, err := ResourceRetrieveEx[any](rpc.ActorSystem, typeName, resourceId, orcapi.PermissionRead, orcapi.ResourceFlags{IncludeOthers: true})
			if err != nil {
				result = util.MergeHttpErr(result, err)
				continue
			}
			if child.Owner != entity.Owner {
				continue
			}
			var deleted []orcapi.AclEntity
			for _, entry := range child.Permissions.Value.Others {
				deleted = append(deleted, entry.Entity)
			}
			err = ResourceUpdateAcl(rpc.ActorSystem, typeName, orcapi.UpdatedAcl{
				Id:      child.Id,
				Added:   stacksCloneAcl(entity.Permissions.Value.Others),
				Deleted: deleted,
			})
			result = util.MergeHttpErr(result, err)
		}
	}
	return result
}

func initStacks() {
	InitResourceType(
		stackType,
		resourceTypeCreateWithoutAdmin,
		func(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {},
		func(batch *db.Batch, resource *resource) {},
		func(resource orcapi.Resource, specification orcapi.ResourceSpecification, extra any, flags orcapi.ResourceFlags, actor rpc.Actor) any {
			return orcapi.Stack{
				Id:          specification.Labels[orcapi.ResourceLabelStackInstance],
				Type:        specification.Labels[orcapi.ResourceLabelStackName],
				CreatedAt:   resource.CreatedAt,
				Permissions: resource.Permissions.Value,
			}
		},
		nil,
	)
	orcapi.StacksBrowse.Handler(func(info rpc.RequestInfo, request orcapi.StacksBrowseRequest) (fndapi.PageV2[orcapi.Stack], *util.HttpError) {
		return StacksBrowse(info.Actor, request.Next, request.ItemsPerPage)
	})

	orcapi.StacksRetrieve.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (orcapi.Stack, *util.HttpError) {
		return StacksRetrieve(info.Actor, request.Id)
	})

	orcapi.StacksDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (util.Empty, *util.HttpError) {
		for _, id := range request.Items {
			StacksDelete(info.Actor, id.Id)
		}
		return util.Empty{}, nil
	})

	orcapi.StacksUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.UpdatedAcl]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			err := StacksUpdateAcl(info.Actor, item)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})

	orcapi.StacksControlRequestDeletion.Handler(func(info rpc.RequestInfo, request orcapi.StacksControlRequestDeletionRequest) (fndapi.FindByIntId, *util.HttpError) {
		providerId, ok := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		if !ok {
			return fndapi.FindByIntId{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		activation := util.OptMap(request.ActivationTime, func(value fndapi.Timestamp) time.Time {
			return value.Time()
		})

		httpErr := (*util.HttpError)(nil)
		id := db.NewTx(func(tx *db.Transaction) int {
			row, rowOk := db.Get[struct{ RequestId int }](
				tx,
				`
					insert into app_orchestrator.stack_deletion_requests (stack_id, provider_filter, activation_time, 
						owner_created_by, owner_project) 
					values (:stack_id, :provider, :time, :username, :project)
					returning request_id
				`,
				db.Params{
					"stack_id": request.Id,
					"provider": providerId,
					"time":     activation.Sql(),
					"username": request.Owner.CreatedBy,
					"project":  request.Owner.Project.Sql(),
				},
			)
			if err := tx.PeekError(); err != nil {
				tx.ConsumeError()
				httpErr = util.HttpErr(http.StatusInternalServerError, "failed to create deletion request")
				return 0
			}
			if !rowOk {
				httpErr = util.HttpErr(http.StatusInternalServerError, "failed to create deletion request")
				return 0
			}
			return row.RequestId
		})

		if httpErr != nil {
			return fndapi.FindByIntId{}, httpErr
		}

		return fndapi.FindByIntId{Id: id}, nil
	})

	orcapi.StacksControlRenewDeletion.Handler(func(info rpc.RequestInfo, request orcapi.StacksControlRenewDeletionRequest) (util.Empty, *util.HttpError) {
		providerId, ok := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		if !ok {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		if !request.ActivationTime.Present {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "missing activation time")
		}

		activation := request.ActivationTime.Value.Time()

		httpErr := (*util.HttpError)(nil)
		renewed := db.NewTx(func(tx *db.Transaction) bool {
			row, rowOk := db.Get[struct{ RequestId int }](
				tx,
				`
					update app_orchestrator.stack_deletion_requests
					set activation_time = :activation
					where request_id = :request_id
						and provider_filter = :provider
						and activation_time is not null
						and now() < activation_time
					returning request_id
				`,
				db.Params{
					"request_id": request.RequestId,
					"provider":   providerId,
					"activation": activation,
				},
			)
			if err := tx.PeekError(); err != nil {
				tx.ConsumeError()
				httpErr = util.HttpErr(http.StatusInternalServerError, "failed to renew deletion request")
				return false
			}
			if !rowOk {
				return false
			}
			return row.RequestId == request.RequestId
		})

		if httpErr != nil {
			return util.Empty{}, httpErr
		}

		if !renewed {
			return util.Empty{}, util.HttpErr(http.StatusConflict, "deletion request is no longer pending")
		}

		return util.Empty{}, nil
	})

	orcapi.StacksControlCancelDeletion.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
		providerId, ok := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		if !ok {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		httpErr := (*util.HttpError)(nil)
		cancelled := db.NewTx(func(tx *db.Transaction) bool {
			row, rowOk := db.Get[struct{ RequestId int }](
				tx,
				`
					delete from app_orchestrator.stack_deletion_requests
					where request_id = :id
						and provider_filter = :provider
						and activation_time is not null
						and now() < activation_time
					returning request_id
				`,
				db.Params{
					"id":       request.Id,
					"provider": providerId,
				},
			)
			if err := tx.PeekError(); err != nil {
				tx.ConsumeError()
				httpErr = util.HttpErr(http.StatusInternalServerError, "failed to cancel deletion request")
				return false
			}
			if !rowOk {
				return false
			}
			return row.RequestId == request.Id
		})

		if httpErr != nil {
			return util.Empty{}, httpErr
		}

		if !cancelled {
			return util.Empty{}, util.HttpErr(http.StatusConflict, "deletion request is no longer pending")
		}

		return util.Empty{}, nil
	})

	go stacksHandleDeletions()
}

func StacksBrowse(actor rpc.Actor, next util.Option[string], itemsPerPage int) (fndapi.PageV2[orcapi.Stack], *util.HttpError) {
	return ResourceBrowse[orcapi.Stack](actor, stackType, next, itemsPerPage, orcapi.ResourceFlags{IncludeOthers: true}, nil, nil), nil
}

func StacksRetrieve(actor rpc.Actor, id string) (orcapi.Stack, *util.HttpError) {
	entity, exists := stacksFindEntity(actor, id)
	if !exists {
		return orcapi.Stack{}, util.HttpErr(http.StatusNotFound, "stack not found")
	}
	stack, _, _, err := ResourceRetrieveEx[orcapi.Stack](actor, stackType, ResourceParseId(entity.Id), orcapi.PermissionRead, orcapi.ResourceFlags{IncludeOthers: true})
	if err != nil {
		return orcapi.Stack{}, err
	}
	flags := orcapi.ResourceFlags{
		FilterLabels: map[string]string{
			orcapi.ResourceLabelStackEntity: entity.Id,
		},
		SortBy:        util.OptValue("createdAt"),
		SortDirection: util.OptValue(orcapi.SortDirectionAscending),
	}
	stackStatus := orcapi.StackStatus{}
	stackStatus.Jobs = fndapi.BrowseAll(0, func(next util.Option[string]) fndapi.PageV2[orcapi.Job] {
		page, pageErr := JobsBrowse(actor, next, 250, orcapi.JobFlags{IncludeApplication: true, ResourceFlags: flags})
		err = util.MergeHttpErr(err, pageErr)
		return page
	})
	stackStatus.Licenses = fndapi.BrowseAll(0, func(next util.Option[string]) fndapi.PageV2[orcapi.License] {
		page, pageErr := ResourceCatalogs.Licenses.Browse(actor, 250, next, flags)
		err = util.MergeHttpErr(err, pageErr)
		return page
	})
	stackStatus.PublicIps = fndapi.BrowseAll(0, func(next util.Option[string]) fndapi.PageV2[orcapi.PublicIp] {
		page, pageErr := ResourceCatalogs.PublicIps.Browse(actor, 250, next, flags)
		err = util.MergeHttpErr(err, pageErr)
		return page
	})
	stackStatus.PublicLinks = fndapi.BrowseAll(0, func(next util.Option[string]) fndapi.PageV2[orcapi.Ingress] {
		page, pageErr := ResourceCatalogs.PublicLinks.Browse(actor, 250, next, flags)
		err = util.MergeHttpErr(err, pageErr)
		return page
	})
	stackStatus.Networks = fndapi.BrowseAll(0, func(next util.Option[string]) fndapi.PageV2[orcapi.PrivateNetwork] {
		page, pageErr := ResourceCatalogs.Networks.Browse(actor, 250, next, flags)
		err = util.MergeHttpErr(err, pageErr)
		return page
	})
	stackStatus.Services = fndapi.BrowseAll(0, func(next util.Option[string]) fndapi.PageV2[orcapi.Service] {
		page, pageErr := ResourceCatalogs.Services.Browse(actor, 250, next, flags)
		err = util.MergeHttpErr(err, pageErr)
		return page
	})

	if err != nil {
		return orcapi.Stack{}, err
	}

	var filteredJobs []orcapi.Job
	for _, job := range stackStatus.Jobs {
		if job.Status.State.IsFinal() {
			continue
		}

		filteredJobs = append(filteredJobs, job)
	}
	stackStatus.Jobs = filteredJobs

	stackStatus.UcxUiMode = orcapi.UcxUiNone
	stackStatus.UcxConnectJobId = util.OptNone[string]()

	oldestCandidate := util.OptNone[string]()
	for _, job := range stackStatus.Jobs {
		if job.Specification.Labels == nil {
			continue
		}

		portLabel, hasUcxPort := job.Specification.Labels[resourceLabelUcxPort]
		if !hasUcxPort {
			continue
		}

		port, err := strconv.Atoi(portLabel)
		if err != nil || port <= 0 || port > 65535 {
			continue
		}

		if !oldestCandidate.Present {
			oldestCandidate.Set(job.Id)
		}

		if job.Status.State == orcapi.JobStateRunning {
			stackStatus.UcxConnectJobId.Set(job.Id)
			break
		}
	}

	if !stackStatus.UcxConnectJobId.Present && oldestCandidate.Present {
		stackStatus.UcxConnectJobId = oldestCandidate
	}

	if stackStatus.UcxConnectJobId.Present {
		stackStatus.UcxUiMode = orcapi.UcxUiReplacement
	}

	stack.Status = util.OptValue(stackStatus)
	return stack, nil
}

func StacksDelete(actor rpc.Actor, id string) {
	db.NewTx0(func(tx *db.Transaction) {
		projectId := util.OptMap(actor.Project, func(value rpc.ProjectId) string {
			return string(value)
		})

		db.Exec(
			tx,
			`
				insert into app_orchestrator.stack_deletion_requests (stack_id, provider_filter, activation_time, 
					owner_created_by, owner_project) 
				values (:stack_id, null, null, :username, :project)
		    `,
			db.Params{
				"stack_id": id,
				"username": actor.Username,
				"project":  projectId.Sql(),
			},
		)
	})
}

func StacksUpdateAcl(actor rpc.Actor, request orcapi.UpdatedAcl) *util.HttpError {
	stacksMutationMu.Lock()
	defer stacksMutationMu.Unlock()
	entity, exists := stacksFindEntity(actor, request.Id)
	if !exists {
		return util.HttpErr(http.StatusNotFound, "stack not found")
	}
	for _, entry := range request.Added {
		if entry.Entity.Type != orcapi.AclEntityTypeProjectGroup {
			return util.HttpErr(http.StatusBadRequest, "stack grants must use project groups")
		}
		for _, permission := range entry.Permissions {
			if permission != orcapi.PermissionRead && permission != orcapi.PermissionEdit {
				return util.HttpErr(http.StatusBadRequest, "stack grants must use READ or EDIT")
			}
		}
	}
	update := request
	update.Id = entity.Id
	if err := ResourceUpdateAcl(actor, stackType, update); err != nil {
		return err
	}
	_, entity, _, err := ResourceRetrieveEx[orcapi.Stack](actor, stackType, ResourceParseId(entity.Id), orcapi.PermissionRead, orcapi.ResourceFlags{IncludeOthers: true})
	if err != nil {
		return err
	}
	return stacksApplyAcl(entity)
}

func stacksHandleDeletions() {
	type deletionRequest struct {
		RequestId      int
		StackId        string
		ProviderFilter sql.Null[string]
		ActivationTime sql.Null[time.Time]
		OwnerCreatedBy string
		OwnerProject   sql.Null[string]
	}

	for {
		requests := db.NewTx(func(tx *db.Transaction) []deletionRequest {
			return db.Select[deletionRequest](
				tx,
				`
					select request_id, stack_id, provider_filter, activation_time, owner_created_by, owner_project
					from app_orchestrator.stack_deletion_requests
					where activation_time is null or now() >= activation_time
				`,
				db.Params{},
			)
		})

		var handled []int

		for _, req := range requests {
			actor, ok := rpc.LookupActor(req.OwnerCreatedBy)
			if !ok {
				log.Info("Attempting to delete stack %v but owner is not known (%v)!", req.StackId, req.OwnerCreatedBy)
				handled = append(handled, req.RequestId)
				continue
			}
			if req.OwnerProject.Valid {
				_, isMember := actor.Membership[rpc.ProjectId(req.OwnerProject.V)]
				if isMember {
					actor.Project.Set(rpc.ProjectId(req.OwnerProject.V))
				} else {
					log.Info("Unable to delete stack, stack owner can no longer perform this action: %v", req.StackId)
					handled = append(handled, req.RequestId)
					continue
				}
			}

			stack, err := StacksRetrieve(actor, req.StackId)
			if err != nil {
				handled = append(handled, req.RequestId)
				continue
			}

			ok = true
			complete := true

			stackStatus := stack.Status.Value
			for _, job := range stackStatus.Jobs {
				if req.ProviderFilter.Valid && req.ProviderFilter.V != job.Specification.Product.Provider {
					complete = false
					continue
				}

				err = ResourceCatalogs.Jobs.Delete(actor, job.Id)
				if err != nil && err.StatusCode != http.StatusNotFound && err.StatusCode != http.StatusForbidden {
					ok = false
				}
			}

			for _, resc := range stackStatus.Licenses {
				if req.ProviderFilter.Valid && req.ProviderFilter.V != resc.Specification.Product.Provider {
					complete = false
					continue
				}

				err = ResourceCatalogs.Licenses.Delete(actor, resc.Id)
				if err != nil && err.StatusCode != http.StatusNotFound && err.StatusCode != http.StatusForbidden {
					ok = false
				}
			}

			for _, resc := range stackStatus.PublicIps {
				if req.ProviderFilter.Valid && req.ProviderFilter.V != resc.Specification.Product.Provider {
					complete = false
					continue
				}

				err = ResourceCatalogs.PublicIps.Delete(actor, resc.Id)
				if err != nil && err.StatusCode != http.StatusNotFound && err.StatusCode != http.StatusForbidden {
					ok = false
				}
			}

			for _, resc := range stackStatus.PublicLinks {
				if req.ProviderFilter.Valid && req.ProviderFilter.V != resc.Specification.Product.Provider {
					complete = false
					continue
				}

				err = ResourceCatalogs.PublicLinks.Delete(actor, resc.Id)
				if err != nil && err.StatusCode != http.StatusNotFound && err.StatusCode != http.StatusForbidden {
					ok = false
				}
			}

			for _, resc := range stackStatus.Services {
				if req.ProviderFilter.Valid && req.ProviderFilter.V != resc.Specification.Product.Provider {
					complete = false
					continue
				}

				err = ResourceCatalogs.Services.Delete(actor, resc.Id)
				if err != nil && err.StatusCode != http.StatusNotFound && err.StatusCode != http.StatusForbidden {
					ok = false
				}
			}

			for _, resc := range stackStatus.Networks {
				if req.ProviderFilter.Valid && req.ProviderFilter.V != resc.Specification.Product.Provider {
					complete = false
					continue
				}

				err = ResourceCatalogs.Networks.Delete(actor, resc.Id)
				if err != nil && err.StatusCode != http.StatusNotFound && err.StatusCode != http.StatusForbidden {
					ok = false
				}
			}

			if ok {
				if complete {
					if entity, exists := stacksFindEntity(actor, req.StackId); exists {
						ResourceDelete(actor, stackType, ResourceParseId(entity.Id))
					}
				}
				handled = append(handled, req.RequestId)
			}
		}

		if len(handled) > 0 {
			db.NewTx0(func(tx *db.Transaction) {
				b := db.BatchNew(tx)
				for _, reqId := range handled {
					db.BatchExec(
						b,
						`delete from app_orchestrator.stack_deletion_requests where request_id = :id`,
						db.Params{"id": reqId},
					)
				}
				db.BatchSend(b)
			})
		}

		time.Sleep(1 * time.Second)
	}
}
