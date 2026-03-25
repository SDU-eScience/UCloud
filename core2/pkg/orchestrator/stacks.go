package orchestrator

import (
	"cmp"
	"database/sql"
	"net/http"
	"slices"
	"strings"
	"time"

	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initStacks() {
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

		id := db.NewTx(func(tx *db.Transaction) int {
			row, _ := db.Get[struct{ RequestId int }](
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
			return row.RequestId
		})

		return fndapi.FindByIntId{Id: id}, nil
	})

	orcapi.StacksControlCancelDeletion.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
		providerId, ok := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		if !ok {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					delete from app_orchestrator.stack_deletion_requests
					where request_id = :id and provider_filter = :provider
			    `,
				db.Params{
					"id":       request.Id,
					"provider": providerId,
				},
			)
		})

		return util.Empty{}, nil
	})

	go stacksHandleDeletions()
}

func StacksBrowse(actor rpc.Actor, next util.Option[string], itemsPerPage int) (fndapi.PageV2[orcapi.Stack], *util.HttpError) {
	itemsPerPage = fndapi.ItemsPerPage(itemsPerPage)
	result := fndapi.PageV2[orcapi.Stack]{ItemsPerPage: itemsPerPage}

	stacksById := map[string]orcapi.Stack{}

	for {
		if len(stacksById) > itemsPerPage {
			break
		}

		jobs, err := JobsBrowse(
			actor,
			next,
			itemsPerPage,
			orcapi.JobFlags{
				ResourceFlags: orcapi.ResourceFlags{
					IncludeOthers: true,
					FilterLabels: map[string]string{
						resourceLabelStack: "true",
					},
				},
			},
		)

		if err != nil {
			return fndapi.PageV2[orcapi.Stack]{}, err
		}

		for _, job := range jobs.Items {
			if job.Status.State.IsFinal() {
				continue
			}

			jobStack := orcapi.Stack{
				Id:          job.Specification.Labels[resourceLabelStackInstance],
				Type:        job.Specification.Labels[resourceLabelStackName],
				CreatedAt:   job.CreatedAt,
				Permissions: job.Permissions.Value,
			}

			if _, exists := stacksById[jobStack.Id]; !exists {
				stacksById[jobStack.Id] = jobStack
			}
		}

		next = jobs.Next
		if !next.Present {
			break
		}
	}

	var stacks []orcapi.Stack
	for _, stack := range stacksById {
		stacks = append(stacks, stack)
	}

	slices.SortFunc(stacks, func(a, b orcapi.Stack) int {
		return cmp.Compare(a.CreatedAt.Time().UnixMilli(), b.CreatedAt.Time().UnixMilli()) * -1
	})

	result.Items = stacks
	result.Next = next
	return result, nil
}

func StacksRetrieve(actor rpc.Actor, id string) (orcapi.Stack, *util.HttpError) {
	flags := orcapi.ResourceFlags{
		FilterLabels: map[string]string{
			resourceLabelStackInstance: id,
		},
		SortBy:        util.OptValue("createdAt"),
		SortDirection: util.OptValue(orcapi.SortDirectionAscending),
	}

	var err *util.HttpError
	stackStatus := orcapi.StackStatus{}
	stackStatus.Jobs = fndapi.BrowseAll(0, func(next util.Option[string]) fndapi.PageV2[orcapi.Job] {
		// NOTE(Dan): This purposefully returns a stack even if none of the jobs are in a non-terminal state. This is
		// needed to make the cleanup procedure easier in case of a partial failure where all the jobs are stopped,
		// but some of the other resources are left dangling.

		page, pageErr := ResourceCatalogs.Jobs.Browse(actor, 250, next, flags)
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

	if err != nil {
		return orcapi.Stack{}, err
	}

	if len(stackStatus.Jobs) == 0 {
		return orcapi.Stack{}, util.HttpErr(http.StatusNotFound, "stack not found")
	}

	referenceJob := stackStatus.Jobs[0]

	var filteredJobs []orcapi.Job
	for _, job := range stackStatus.Jobs {
		if job.Status.State.IsFinal() {
			continue
		}

		filteredJobs = append(filteredJobs, job)
	}
	stackStatus.Jobs = filteredJobs
	if len(stackStatus.Jobs) > 0 {
		referenceJob = stackStatus.Jobs[0]
	}

	isEmpty := len(stackStatus.Jobs) == 0
	isEmpty = isEmpty && len(stackStatus.Licenses) == 0
	isEmpty = isEmpty && len(stackStatus.PublicIps) == 0
	isEmpty = isEmpty && len(stackStatus.PublicLinks) == 0
	isEmpty = isEmpty && len(stackStatus.Networks) == 0

	if isEmpty {
		return orcapi.Stack{}, util.HttpErr(http.StatusNotFound, "stack not found")
	}

	return orcapi.Stack{
		Id:          referenceJob.Specification.Labels[resourceLabelStackInstance],
		Type:        referenceJob.Specification.Labels[resourceLabelStackName],
		CreatedAt:   referenceJob.CreatedAt,
		Permissions: referenceJob.Permissions.Value,
		Status:      util.OptValue(stackStatus),
	}, nil
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
	stack, err := StacksRetrieve(actor, request.Id)
	if err != nil {
		return err
	}

	updateRequest := func(id string) orcapi.UpdatedAcl {
		return orcapi.UpdatedAcl{
			Id:      id,
			Added:   request.Added,
			Deleted: request.Deleted,
		}
	}

	for _, job := range stack.Status.Value.Jobs {
		serr := ResourceUpdateAcl(actor, jobType, updateRequest(job.Id))
		err = util.MergeHttpErr(err, serr)
	}

	for _, resc := range stack.Status.Value.Licenses {
		serr := ResourceUpdateAcl(actor, licenseType, updateRequest(resc.Id))
		err = util.MergeHttpErr(err, serr)
	}

	for _, resc := range stack.Status.Value.PublicIps {
		serr := ResourceUpdateAcl(actor, publicIpType, updateRequest(resc.Id))
		err = util.MergeHttpErr(err, serr)
	}

	for _, resc := range stack.Status.Value.PublicLinks {
		serr := ResourceUpdateAcl(actor, ingressType, updateRequest(resc.Id))
		err = util.MergeHttpErr(err, serr)
	}

	for _, resc := range stack.Status.Value.Networks {
		serr := ResourceUpdateAcl(actor, privateNetworkType, updateRequest(resc.Id))
		err = util.MergeHttpErr(err, serr)
	}

	return err
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

			stackStatus := stack.Status.Value
			for _, job := range stackStatus.Jobs {
				if req.ProviderFilter.Valid && req.ProviderFilter.V != job.Specification.Product.Provider {
					continue
				}

				err = ResourceCatalogs.Jobs.Delete(actor, job.Id)
				if err != nil && err.StatusCode != http.StatusNotFound && err.StatusCode != http.StatusForbidden {
					ok = false
				}
			}

			for _, resc := range stackStatus.Licenses {
				if req.ProviderFilter.Valid && req.ProviderFilter.V != resc.Specification.Product.Provider {
					continue
				}

				err = ResourceCatalogs.Licenses.Delete(actor, resc.Id)
				if err != nil && err.StatusCode != http.StatusNotFound && err.StatusCode != http.StatusForbidden {
					ok = false
				}
			}

			for _, resc := range stackStatus.PublicIps {
				if req.ProviderFilter.Valid && req.ProviderFilter.V != resc.Specification.Product.Provider {
					continue
				}

				err = ResourceCatalogs.PublicIps.Delete(actor, resc.Id)
				if err != nil && err.StatusCode != http.StatusNotFound && err.StatusCode != http.StatusForbidden {
					ok = false
				}
			}

			for _, resc := range stackStatus.PublicLinks {
				if req.ProviderFilter.Valid && req.ProviderFilter.V != resc.Specification.Product.Provider {
					continue
				}

				err = ResourceCatalogs.PublicLinks.Delete(actor, resc.Id)
				if err != nil && err.StatusCode != http.StatusNotFound && err.StatusCode != http.StatusForbidden {
					ok = false
				}
			}

			for _, resc := range stackStatus.Networks {
				if req.ProviderFilter.Valid && req.ProviderFilter.V != resc.Specification.Product.Provider {
					continue
				}

				err = ResourceCatalogs.Networks.Delete(actor, resc.Id)
				if err != nil && err.StatusCode != http.StatusNotFound && err.StatusCode != http.StatusForbidden {
					ok = false
				}
			}

			if ok {
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
