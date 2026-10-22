package orchestrator

import (
	"database/sql"
	"net/http"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
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

func stacksControlOwnerActor(owner orcapi.ResourceOwner) (rpc.Actor, *util.HttpError) {
	actor, ok := rpc.LookupActor(owner.CreatedBy)
	if !ok {
		return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "forbidden")
	}

	if owner.Project.Present {
		projectId := rpc.ProjectId(owner.Project.Value)
		if _, isMember := actor.Membership[projectId]; !isMember {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}
		actor.Project.Set(projectId)
	} else {
		actor.Project = util.OptNone[rpc.ProjectId]()
	}

	return actor, nil
}

func StacksCreate(actor rpc.Actor, id string, name string, stateFolder string, provider string, application orcapi.NameAndVersion) *util.HttpError {
	if strings.TrimSpace(id) == "" || strings.TrimSpace(name) == "" {
		return util.HttpErr(http.StatusBadRequest, "stack id and type are required")
	}
	if strings.TrimSpace(application.Name) == "" || strings.TrimSpace(application.Version) == "" {
		return util.HttpErr(http.StatusBadRequest, "the application name and version are required")
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
		ServerLabels: map[string]string{
			orcapi.ResourceLabelStack:            "true",
			orcapi.ResourceLabelStackInstance:    id,
			orcapi.ResourceLabelStackName:        name,
			orcapi.ResourceLabelStackStateFolder: stateFolder,
			orcapi.ResourceLabelStackProvider:    provider,
			orcapi.ResourceLabelUcxAppName:       application.Name,
			orcapi.ResourceLabelUcxAppVersion:    application.Version,
		},
	}, nil)
	if err != nil {
		return err
	}
	ResourceConfirm(stackType, resourceId)
	if stateDrive.Present {
		labels := util.MapMerge(stateDrive.Value.Specification.Labels, map[string]string{
			orcapi.ResourceLabelStack:       "true",
			orcapi.ResourceLabelStackEntity: strconv.FormatUint(uint64(resourceId), 10),
		})
		return resourceUpdateLabelsEx(actor, driveType, stateDrive.Value.Id, labels, orcapi.PermissionAdmin, true, false)
	}
	return nil
}

func resourceValidateSameStack(specification orcapi.ResourceSpecification, referencedLabels map[string]string) *util.HttpError {
	currentInstance := strings.TrimSpace(specification.ServerLabels[orcapi.ResourceLabelStackInstance])
	referencedInstance := strings.TrimSpace(referencedLabels[orcapi.ResourceLabelStackInstance])
	if currentInstance == "" || referencedInstance != currentInstance {
		return util.HttpErr(http.StatusBadRequest, "the referenced resource must belong to the same stack")
	}

	currentEntity := strings.TrimSpace(specification.ServerLabels[orcapi.ResourceLabelStackEntity])
	referencedEntity := strings.TrimSpace(referencedLabels[orcapi.ResourceLabelStackEntity])
	if currentEntity != "" && referencedEntity != "" && currentEntity != referencedEntity {
		return util.HttpErr(http.StatusBadRequest, "the referenced resource must belong to the same stack")
	}

	return nil
}

func stacksPrepareResource(actor rpc.Actor, specification *orcapi.ResourceSpecification, requireStack bool) *util.HttpError {
	id := strings.TrimSpace(specification.ServerLabels[orcapi.ResourceLabelStackInstance])
	if id == "" {
		if requireStack {
			return util.HttpErr(http.StatusForbidden, "the stack of this request is required")
		}
		return nil
	}
	entity, exists := stacksFindEntity(actor, id)
	if !exists {
		if requireStack {
			return util.HttpErr(http.StatusForbidden, "the stack of this request is not available")
		}
		return nil
	}
	_, stack, stackSpec, err := ResourceRetrieveEx[orcapi.Stack](rpc.ActorSystem, stackType, ResourceParseId(entity.Id), orcapi.PermissionRead, orcapi.ResourceFlags{IncludeOthers: true})
	if err != nil {
		return err
	}
	stackInstance := stackSpec.Labels[orcapi.ResourceLabelStackInstance]
	if strings.TrimSpace(stackInstance) != id {
		return util.HttpErr(http.StatusForbidden, "the stack is not available")
	}

	stack.Permissions.Value.Others = stacksCloneAcl(stack.Permissions.Value.Others)
	specification.StackResource = &stack
	if specification.ServerLabels == nil {
		specification.ServerLabels = map[string]string{}
	}
	for _, key := range resourceStackIdentityLabels {
		delete(specification.ServerLabels, key)
		if value, exists := stackSpec.Labels[key]; exists {
			specification.ServerLabels[key] = value
		}
	}
	specification.ServerLabels[orcapi.ResourceLabelStack] = "true"
	specification.ServerLabels[orcapi.ResourceLabelStackEntity] = entity.Id
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

type internalStack struct {
	InstanceId string
	Name       string
}

func stackLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	for _, id := range ids {
		r, ok := resources[ResourceId(id)]
		if !ok {
			continue
		}

		r.Extra = &internalStack{
			InstanceId: r.BaseSpec.Labels[orcapi.ResourceLabelStackInstance],
			Name:       r.BaseSpec.Labels[orcapi.ResourceLabelStackName],
		}
	}
}

func initStacks() {
	InitResourceType(
		stackType,
		resourceTypeCreateWithoutAdmin,
		stackLoad,
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

	orcapi.StacksDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.StacksDeleteRequest]) (util.Empty, *util.HttpError) {
		for _, id := range request.Items {
			if err := StacksDelete(info.Actor, id.Id, id.DeleteStateDrive); err != nil {
				return util.Empty{}, err
			}
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

	orcapi.StacksSpawnDeclaredJob.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (util.Empty, *util.HttpError) {
		return util.Empty{}, stacksSpawnDeclaredJob(info.Actor, request.Id)
	})

	orcapi.StacksControlRequestDeletion.Handler(func(info rpc.RequestInfo, request orcapi.StacksControlRequestDeletionRequest) (fndapi.FindByIntId, *util.HttpError) {
		providerId, ok := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		if !ok {
			return fndapi.FindByIntId{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		activation := util.OptMap(request.ActivationTime, func(value fndapi.Timestamp) time.Time {
			return value.Time()
		})

		id := db.NewTx(func(tx *db.Transaction) int {
			row, rowOk := db.Get[struct{ RequestId int }](
				tx,
				`
					insert into app_orchestrator.stack_deletion_requests (stack_id, provider_filter, activation_time, 
						owner_created_by, owner_project, delete_state_drive)
					values (:stack_id, :provider, :time, :username, :project, :delete_state_drive)
					returning request_id
				`,
				db.Params{
					"stack_id":           request.Id,
					"provider":           providerId,
					"time":               activation.Sql(),
					"username":           request.Owner.CreatedBy,
					"project":            request.Owner.Project.Sql(),
					"delete_state_drive": request.DeleteStateDrive,
				},
			)
			if !rowOk {
				log.Fatal("failed to create deletion request")
			}
			return row.RequestId
		})

		return fndapi.FindByIntId{Id: id}, nil
	})

	orcapi.StacksControlCheckEdit.Handler(func(info rpc.RequestInfo, request orcapi.StacksControlCheckEditRequest) (util.Empty, *util.HttpError) {
		if _, isProvider := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix); !isProvider {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		actor, actorErr := stacksControlOwnerActor(request.Owner)
		if actorErr != nil {
			return util.Empty{}, actorErr
		}

		_, _, specification, editErr := ResourceRetrieveEx[orcapi.Stack](
			actor,
			stackType,
			ResourceParseId(request.StackEntityId),
			orcapi.PermissionEdit,
			orcapi.ResourceFlags{IncludeOthers: true},
		)
		if editErr != nil {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}
		if specification.Labels[orcapi.ResourceLabelStackInstance] != request.Id {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		return util.Empty{}, nil
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
			if !rowOk {
				return false
			}
			return row.RequestId == request.RequestId
		})

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
			if !rowOk {
				return false
			}
			return row.RequestId == request.Id
		})

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
	stack, _, stackSpec, err := ResourceRetrieveEx[orcapi.Stack](actor, stackType, ResourceParseId(entity.Id), orcapi.PermissionRead, orcapi.ResourceFlags{IncludeOthers: true})
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

	appName := strings.TrimSpace(stackSpec.Labels[orcapi.ResourceLabelUcxAppName])
	appVersion := strings.TrimSpace(stackSpec.Labels[orcapi.ResourceLabelUcxAppVersion])
	if appName != "" && appVersion != "" {
		if app, ok := AppRetrieve(actor, appName, appVersion, AppDiscoveryAll, 0); ok {
			ucx := app.Invocation.Ucx
			if ucx.Present && ucx.Value.DisasterRecovery.Present && ucx.Value.DisasterRecovery.Value.Enabled {
				stackStatus.DisasterRecoveryDeclared = true
			}
		}
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

	if candidate, hasCandidate := stacksUcxUiJob(entity.Id, stackStatus.Jobs); hasCandidate {
		stackStatus.UcxConnectJobId.Set(candidate)
		stackStatus.UcxUiMode = orcapi.UcxUiReplacement
		ucxUiRememberSelectedJob(entity.Id, candidate)
	}

	stack.Status = util.OptValue(stackStatus)
	return stack, nil
}

// stacksUcxUiJob selects the job that serves the user interface of the stack. The previous choice
// is kept while it runs and answers. It moves to another running job when the previous one stops
// answering, for example while the machine that hosts it reboots during maintenance. A running
// disaster recovery job takes precedence.
func stacksUcxUiJob(entityId string, jobs []orcapi.Job) (string, bool) {
	running := []string{}
	fallback := []string{}
	disasterRecovery := ""

	for _, job := range jobs {
		if _, hasUcxPort := stacksUcxUiPort(job); !hasUcxPort {
			continue
		}

		if strings.EqualFold(job.Specification.Labels[orcapi.ResourceLabelDisasterRecovery], "true") {
			if job.Status.State == orcapi.JobStateRunning && disasterRecovery == "" {
				disasterRecovery = job.Id
			}
			continue
		}

		if job.Status.State == orcapi.JobStateRunning {
			running = append(running, job.Id)
			continue
		}
		fallback = append(fallback, job.Id)
	}

	if disasterRecovery != "" {
		return disasterRecovery, true
	}

	if previous := ucxUiSelectedJob(entityId); previous != "" {
		for _, jobId := range running {
			if jobId == previous && ucxUiJobAvailable(jobId) {
				return previous, true
			}
		}
	}

	for _, jobId := range running {
		if ucxUiJobAvailable(jobId) {
			return jobId, true
		}
	}
	for _, jobId := range fallback {
		if ucxUiJobAvailable(jobId) {
			return jobId, true
		}
	}
	if len(fallback) > 0 {
		return fallback[0], true
	}
	if len(running) > 0 {
		return running[0], true
	}
	return "", false
}

func stacksUcxUiPort(job orcapi.Job) (int, bool) {
	portLabel, hasUcxPort := job.Specification.Labels[resourceLabelUcxPort]
	if !hasUcxPort {
		return 0, false
	}

	port, err := strconv.Atoi(portLabel)
	if err != nil || port <= 0 || port > 65535 {
		return 0, false
	}
	return port, true
}

// stacksUcxUiAlternativeJob finds another running UI job of the same stack. It is used when the
// selected job cannot be reached, for example while its machine reboots during maintenance.
func stacksUcxUiAlternativeJob(actor rpc.Actor, current orcapi.Job) (orcapi.Job, int, bool) {
	entityId := strings.TrimSpace(current.Specification.Labels[orcapi.ResourceLabelStackEntity])
	if entityId == "" {
		return orcapi.Job{}, 0, false
	}
	if strings.EqualFold(current.Specification.Labels[orcapi.ResourceLabelDisasterRecovery], "true") {
		return orcapi.Job{}, 0, false
	}

	jobs, jobErr := stacksUcxUiJobs(actor, entityId)
	if jobErr != nil {
		return orcapi.Job{}, 0, false
	}

	for _, job := range jobs {
		if job.Id == current.Id || job.Status.State != orcapi.JobStateRunning {
			continue
		}
		if !ucxUiJobAvailable(job.Id) {
			continue
		}
		if strings.EqualFold(job.Specification.Labels[orcapi.ResourceLabelDisasterRecovery], "true") {
			continue
		}

		port, hasUcxPort := stacksUcxUiPort(job)
		if !hasUcxPort {
			continue
		}
		return job, port, true
	}

	return orcapi.Job{}, 0, false
}

func stacksUcxUiJobs(actor rpc.Actor, entityId string) ([]orcapi.Job, *util.HttpError) {
	var err *util.HttpError
	flags := orcapi.ResourceFlags{
		FilterLabels: map[string]string{
			orcapi.ResourceLabelStackEntity: entityId,
		},
		SortBy:        util.OptValue("createdAt"),
		SortDirection: util.OptValue(orcapi.SortDirectionAscending),
	}

	jobs := fndapi.BrowseAll(0, func(next util.Option[string]) fndapi.PageV2[orcapi.Job] {
		page, pageErr := JobsBrowse(actor, next, 250, orcapi.JobFlags{ResourceFlags: flags})
		err = util.MergeHttpErr(err, pageErr)
		return page
	})
	if err != nil {
		return nil, err
	}
	return jobs, nil
}

func stacksSpawnDeclaredJob(actor rpc.Actor, id string) *util.HttpError {
	stacksMutationMu.Lock()
	defer stacksMutationMu.Unlock()

	entity, exists := stacksFindEntity(actor, id)
	if !exists {
		return util.HttpErr(http.StatusNotFound, "stack not found")
	}

	_, stack, stackSpec, err := ResourceRetrieveEx[orcapi.Stack](
		actor,
		stackType,
		ResourceParseId(entity.Id),
		orcapi.PermissionEdit,
		orcapi.ResourceFlags{IncludeOthers: true},
	)
	if err != nil {
		return err
	}

	jobs := fndapi.BrowseAll(0, func(next util.Option[string]) fndapi.PageV2[orcapi.Job] {
		page, pageErr := JobsBrowse(actor, next, 250, orcapi.JobFlags{
			IncludeApplication: true,
			ResourceFlags: orcapi.ResourceFlags{
				FilterLabels: map[string]string{
					orcapi.ResourceLabelStackEntity: entity.Id,
				},
			},
		})
		err = util.MergeHttpErr(err, pageErr)
		return page
	})
	if err != nil {
		return err
	}

	appName := strings.TrimSpace(stackSpec.Labels[orcapi.ResourceLabelUcxAppName])
	appVersion := strings.TrimSpace(stackSpec.Labels[orcapi.ResourceLabelUcxAppVersion])
	if appName == "" || appVersion == "" {
		return util.HttpErr(http.StatusBadRequest, "the stack has no application")
	}
	app, ok := AppRetrieve(actor, appName, appVersion, AppDiscoveryAll, 0)
	if !ok {
		return util.HttpErr(http.StatusBadRequest, "the application of this stack could not be found")
	}

	disasterRecovery := util.OptNone[orcapi.UcxDisasterRecoveryDescription]()
	resolvedUcx := app.Invocation.Ucx
	if resolvedUcx.Present && resolvedUcx.Value.DisasterRecovery.Present {
		disasterRecovery.Set(resolvedUcx.Value.DisasterRecovery.Value)
	}
	if !disasterRecovery.Present || !disasterRecovery.Value.Enabled {
		return util.HttpErr(http.StatusBadRequest, "the application of this stack does not declare disaster recovery")
	}

	declaration := disasterRecovery.Value.Job
	if len(declaration.Args) == 0 {
		return util.HttpErr(http.StatusBadRequest, "the declared disaster recovery job has no arguments")
	}
	provider := strings.TrimSpace(stackSpec.Labels[orcapi.ResourceLabelStackProvider])
	if provider == "" {
		return util.HttpErr(http.StatusBadRequest, "the stack has no known provider")
	}
	product, productErr := stacksSelectDeclaredJobProduct(entity, provider, jobs, declaration.Cpu, declaration.MemoryInGigs)
	if productErr != nil {
		return productErr
	}

	for i := range jobs {
		job := &jobs[i]
		if job.Specification.Application.Name != "unknown" {
			continue
		}
		if job.Status.State.IsFinal() {
			continue
		}
		if !strings.EqualFold(job.Specification.Labels[orcapi.ResourceLabelStackController], "true") {
			continue
		}
		if strings.TrimSpace(job.Specification.Labels[orcapi.ResourceLabelUcxPort]) == "" {
			continue
		}
		return util.HttpErr(http.StatusConflict, "a recovery job is already running")
	}

	stateFolder := strings.TrimSpace(stackSpec.Labels[orcapi.ResourceLabelStackStateFolder])
	if stateFolder == "" {
		return util.HttpErr(http.StatusBadRequest, "the stack has no state folder")
	}
	stackName := strings.TrimSpace(stackSpec.Labels[orcapi.ResourceLabelStackName])
	if stackName == "" {
		stackName = id
	}

	response, err := InvokeProvider(provider, orcapi.StacksProviderSpawnDeclaredJob, orcapi.StacksProviderSpawnDeclaredJobRequest{
		StackInstance: id,
		StackName:     stackName,
		StackEntityId: entity.Id,
		StateFolder:   stateFolder,
		Owner:         entity.Owner,
		Application:   orcapi.NameAndVersion{Name: appName, Version: appVersion},
		Product:       product,
		Job:           declaration,
	}, ProviderCallOpts{
		Username: util.OptValue(actor.Username),
		Reason:   util.OptValue("Starting disaster recovery for stack " + id),
	})
	if err != nil {
		return err
	}

	acl := stacksCloneAcl(stack.Permissions.Value.Others)
	if len(acl) > 0 {
		_ = ResourceUpdateAcl(rpc.ActorSystem, jobType, orcapi.UpdatedAcl{
			Id:    response.Id,
			Added: acl,
		})
	}

	return nil
}

func stacksSelectDeclaredJobProduct(
	entity orcapi.Resource,
	provider string,
	jobs []orcapi.Job,
	minCpu int,
	minMemoryInGigs int,
) (accapi.ProductReference, *util.HttpError) {
	unlockedCategories, walletErr := stacksDeclaredJobUnlockedCategories(entity, provider)
	if walletErr != nil {
		return accapi.ProductReference{}, walletErr
	}

	controlPlaneCategories := map[string]util.Empty{}
	workerCategories := map[string]util.Empty{}
	for _, job := range jobs {
		if job.Specification.Application.Name == "unknown" || job.Specification.Product.Provider != provider {
			continue
		}

		category := job.Specification.Product.Category
		if category == "" {
			continue
		}

		if strings.EqualFold(job.Specification.Labels[orcapi.ResourceLabelStackController], "true") {
			controlPlaneCategories[category] = util.Empty{}
		} else {
			workerCategories[category] = util.Empty{}
		}
	}

	candidates := SupportRetrieveProducts[orcapi.JobSupport](jobType).ProductsByProvider[provider]

	chosenTier := -1
	var chosen accapi.ProductV2
	found := false
	for _, candidate := range candidates {
		product := candidate.Product
		if product.Gpu != 0 {
			continue
		}
		if product.Category.FreeToUse {
			continue
		}
		if product.HiddenInGrantApplications {
			continue
		}
		if !candidate.Support.Docker.Enabled {
			continue
		}
		if product.Cpu < minCpu || product.MemoryInGigs < minMemoryInGigs {
			continue
		}
		if _, ok := unlockedCategories[product.Category.Name]; !ok {
			continue
		}

		tier := 2
		if _, ok := controlPlaneCategories[product.Category.Name]; ok {
			tier = 0
		} else if _, ok := workerCategories[product.Category.Name]; ok {
			tier = 1
		}

		if !found || tier < chosenTier || (tier == chosenTier && stacksDeclaredJobProductIsSmaller(product, chosen)) {
			chosen = product
			chosenTier = tier
			found = true
		}
	}

	if !found {
		return accapi.ProductReference{}, util.HttpErr(
			http.StatusBadRequest,
			"no product on this provider meets the declared disaster recovery requirements",
		)
	}

	return chosen.ToReference(), nil
}

func stacksDeclaredJobUnlockedCategories(entity orcapi.Resource, provider string) (map[string]util.Empty, *util.HttpError) {
	wallets, err := accapi.WalletsBrowseInternal.Invoke(accapi.WalletsBrowseInternalRequest{
		Owner: orcapi.ResourceOwnerToWalletOwner(entity),
	})
	if err != nil {
		return nil, util.HttpErr(
			http.StatusBadGateway,
			"could not determine the allocations of the workspace - try again later",
		)
	}

	result := map[string]util.Empty{}
	for _, wallet := range wallets.Wallets {
		if wallet.PaysFor.Provider == provider && wallet.MaxUsable > 0 {
			result[wallet.PaysFor.Name] = util.Empty{}
		}
	}
	return result, nil
}

func stacksDeclaredJobProductIsSmaller(a accapi.ProductV2, b accapi.ProductV2) bool {
	if a.Cpu != b.Cpu {
		return a.Cpu < b.Cpu
	}
	if a.MemoryInGigs != b.MemoryInGigs {
		return a.MemoryInGigs < b.MemoryInGigs
	}
	return a.Name < b.Name
}

func stacksDeletionDetails(actor rpc.Actor, id string) (orcapi.Resource, string, []string, *util.HttpError) {
	entity, exists := stacksFindEntity(actor, id)
	if !exists {
		return orcapi.Resource{}, "", nil, util.HttpErr(http.StatusNotFound, "stack not found")
	}
	_, entity, specification, err := ResourceRetrieveEx[orcapi.Stack](actor, stackType, ResourceParseId(entity.Id), orcapi.PermissionEdit, orcapi.ResourceFlags{})
	if err != nil {
		return orcapi.Resource{}, "", nil, err
	}
	var providers []string
	if provider := specification.Labels[orcapi.ResourceLabelStackProvider]; provider != "" {
		providers = append(providers, provider)
	}
	for _, typeName := range []string{jobType, driveType, licenseType, publicIpType, ingressType, privateNetworkType, privateNetworkIpType, serviceType, containerRepositoryType} {
		for _, resourceId := range stacksResourceIds(typeName, entity.Owner, entity.Id) {
			_, child, spec, err := ResourceRetrieveEx[any](rpc.ActorSystem, typeName, resourceId, orcapi.PermissionRead, orcapi.ResourceFlags{})
			if err == nil && child.Owner == entity.Owner && resourceSpecificationHasProduct(spec) {
				providers = util.AppendUnique(providers, spec.Product.Provider)
			}
		}
	}
	stateDrive := ""
	folder := specification.Labels[orcapi.ResourceLabelStackStateFolder]
	if driveId, valid := orcapi.DriveIdFromUCloudPath(folder); valid && folder == "/"+driveId {
		_, drive, spec, err := ResourceRetrieveEx[orcapi.Drive](rpc.ActorSystem, driveType, ResourceParseId(driveId), orcapi.PermissionRead, orcapi.ResourceFlags{})
		if err == nil && drive.Owner == entity.Owner && spec.Labels[orcapi.ResourceLabelStackEntity] == entity.Id {
			stateDrive = driveId
		}
	}
	return entity, stateDrive, util.NonNilSlice(providers), nil
}

func StacksDelete(actor rpc.Actor, id string, deleteStateDrive bool) *util.HttpError {
	entity, _, _, err := stacksDeletionDetails(actor, id)
	if err != nil {
		return err
	}
	db.NewTx0(func(tx *db.Transaction) {
		projectId := util.OptMap(actor.Project, func(value rpc.ProjectId) string {
			return string(value)
		})

		db.Exec(
			tx,
			`
				insert into app_orchestrator.stack_deletion_requests (stack_id, provider_filter, activation_time, 
					owner_created_by, owner_project, delete_state_drive, entity_resource)
				values (:stack_id, null, null, :username, :project, :delete_state_drive, :entity)
				on conflict (entity_resource) where provider_filter is null and entity_resource is not null
				do update set delete_state_drive = app_orchestrator.stack_deletion_requests.delete_state_drive or excluded.delete_state_drive
		    `,
			db.Params{
				"stack_id":           id,
				"username":           actor.Username,
				"project":            projectId.Sql(),
				"delete_state_drive": deleteStateDrive,
				"entity":             int64(ResourceParseId(entity.Id)),
			},
		)
	})
	return nil
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
		RequestId        int
		StackId          string
		ProviderFilter   sql.Null[string]
		ActivationTime   sql.Null[time.Time]
		OwnerCreatedBy   string
		OwnerProject     sql.Null[string]
		DeleteStateDrive bool
	}

	for {
		requests := db.NewTx(func(tx *db.Transaction) []deletionRequest {
			return db.Select[deletionRequest](
				tx,
				`
					select request_id, stack_id, provider_filter, activation_time, owner_created_by, owner_project, delete_state_drive
					from app_orchestrator.stack_deletion_requests
					where activation_time is null or now() >= activation_time
				`,
				db.Params{},
			)
		})

		var handled []int

		for _, req := range requests {
			actor, actorOk := rpc.LookupActor(req.OwnerCreatedBy)
			if !actorOk {
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

			entity, stateDrive, providers, err := stacksDeletionDetails(actor, req.StackId)
			if err != nil && err.StatusCode != http.StatusNotFound {
				log.Warn("Unable to prepare stack deletion %s: %v", req.StackId, err)
				continue
			}
			if req.ProviderFilter.Valid {
				providers = []string{req.ProviderFilter.V}
			}

			owner := orcapi.ResourceOwner{CreatedBy: req.OwnerCreatedBy, Project: util.SqlNullToOpt(req.OwnerProject)}
			stack := orcapi.Stack{Status: util.OptValue(orcapi.StackStatus{})}
			if entity.Id != "" {
				owner = entity.Owner
				stack, err = StacksRetrieve(actor, req.StackId)
				if err != nil {
					log.Warn("Unable to retrieve stack during deletion %s: %v", req.StackId, err)
					continue
				}
			}

			ok := true

			for _, provider := range providers {
				_, err := InvokeProvider(provider, orcapi.StacksProviderDelete, orcapi.StacksProviderDeleteRequest{Id: req.StackId, Owner: owner}, ProviderCallOpts{})
				if err == nil {
					continue
				}
				if err.StatusCode == http.StatusBadRequest || err.StatusCode == http.StatusNotFound || err.StatusCode == http.StatusServiceUnavailable {
					log.Info("Provider %s does not handle stack deletion for %s; continuing", provider, req.StackId)
					continue
				}
				log.Warn("Unable to notify provider %s of stack deletion %s: %v", provider, req.StackId, err)
				ok = false
			}

			if !ok {
				continue
			}

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

			if !ok {
				continue
			}

			if req.DeleteStateDrive && stateDrive != "" {
				_, _, specification, err := ResourceRetrieveEx[orcapi.Drive](rpc.ActorSystem, driveType, ResourceParseId(stateDrive), orcapi.PermissionRead, orcapi.ResourceFlags{})
				if err == nil && (!req.ProviderFilter.Valid || req.ProviderFilter.V == specification.Product.Provider) {
					_, err = DriveDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: stateDrive}))
				} else if err != nil && (err.StatusCode == http.StatusNotFound || err.StatusCode == http.StatusForbidden) {
					err = nil
				}
				if err != nil {
					log.Warn("Unable to delete state drive for stack %s: %v", req.StackId, err)
					continue
				}
			}

			if complete && entity.Id != "" {
				ResourceDelete(actor, stackType, ResourceParseId(entity.Id))
			}

			handled = append(handled, req.RequestId)
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
