package orchestrator

import (
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	ws "github.com/gorilla/websocket"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const (
	jobType = "job"
)

func initJobs() {
	InitResourceType(
		jobType,
		resourceTypeCreateWithoutAdmin,
		jobLoad,
		jobPersist,
		jobTransform,
		jobPersistCommitted,
	)

	jobNotificationsPending.EntriesByUser = map[string]map[string]orcapi.Job{}

	go jobNotificationsLoopSendPending()

	orcapi.JobsCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.JobSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		// TODO Check if we have an allocation?
		var ids []fndapi.FindByStringId
		for _, item := range request.Items {
			spec := item
			err := jobsValidateForSubmission(info.Actor, &spec)
			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			support, ok := SupportByProduct[orcapi.JobSupport](jobType, spec.Product)
			if !ok {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusInternalServerError, "internal error")
			}

			encodedParams, _ := json.Marshal(spec.Parameters)
			encodedResources, _ := json.Marshal(spec.Resources)
			encodedProduct, _ := json.Marshal(support.Product)
			encodedSupport, _ := json.Marshal(support.ResolvedSupport)
			encodedMachineType, _ := json.Marshal(map[string]any{
				"cpu":          support.Product.Cpu,
				"memoryInGigs": support.Product.MemoryInGigs,
			})

			extra := &internalJob{
				Application:    spec.Application,
				Name:           spec.Name,
				Replicas:       spec.Replicas,
				Parameters:     spec.Parameters,
				Resources:      spec.Resources,
				TimeAllocation: spec.TimeAllocation,
				OpenedFile:     spec.OpenedFile,
				SshEnabled:     spec.SshEnabled,
				State:          orcapi.JobStateInQueue,
				JobParametersJson: orcapi.ExportedParameters{
					SiteVersion: 3,
					Request: orcapi.ExportedParametersRequest{
						Application:       spec.Application,
						Product:           spec.Product,
						Name:              spec.Name,
						Replicas:          spec.Replicas,
						Parameters:        encodedParams,
						Resources:         encodedResources,
						TimeAllocation:    spec.TimeAllocation.GetOrDefault(orcapi.SimpleDuration{}),
						ResolvedProduct:   encodedProduct,
						ResolvedSupport:   encodedSupport,
						AllowDuplicateJob: false,
						SshEnabled:        spec.SshEnabled,
					},
					ResolvedResources: orcapi.ExportedParametersResources{
						// TODO
					},
					MachineType: encodedMachineType,
				},
				StartedAt: util.OptNone[fndapi.Timestamp](),
				Updates:   nil,
			}

			job, err := ResourceCreateThroughProvider(info.Actor, jobType, spec.Product, extra, orcapi.JobsProviderCreate)
			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}

			for _, param := range spec.Parameters {
				jobBindResource(job.Id, param)
			}

			for _, resc := range spec.Resources {
				jobBindResource(job.Id, resc)
			}

			ids = append(ids, fndapi.FindByStringId{Id: job.Id})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: ids}, nil
	})

	orcapi.JobsControlAddUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.JobUpdate]]) (util.Empty, *util.HttpError) {
		now := time.Now()

		updatesById := map[string][]orcapi.JobUpdate{}
		for _, item := range request.Items {
			updatesById[item.Id] = append(updatesById[item.Id], item.Update)
		}

		for jobId, updates := range updatesById {
			ok := ResourceUpdate(info.Actor, jobType, ResourceParseId(jobId), orcapi.PermissionProvider, func(r *resource, mapped orcapi.Job) {
				job := r.Extra.(*internalJob)
				job.ChangeFlags |= internalJobPartialChange | internalJobChangeUpdates | internalJobChangeMetadata

				for _, update := range updates {
					update.Timestamp = fndapi.Timestamp(now)

					shouldApply := true
					if s := update.ExpectedState; s.Present {
						shouldApply = job.State == s.Value
					} else if s := update.ExpectedDifferentState; s.Present && s.Value && update.State.Present {
						shouldApply = job.State != update.State.Value
					}

					if job.State.IsFinal() && update.State.Present && !update.State.Value.IsFinal() {
						shouldApply = false
					}

					if shouldApply {
						if s := update.State; s.Present {
							job.State = s.Value

							if job.State == orcapi.JobStateRunning {
								job.StartedAt.Set(fndapi.Timestamp(time.Now()))
							}

							mapped.Status.State = job.State
							jobNotifyStateChange(mapped)

							if job.State.IsFinal() {
								for _, param := range job.Parameters {
									jobUnbindResource(jobId, param)
								}

								for _, resc := range job.Resources {
									jobUnbindResource(jobId, resc)
								}
							}
						}

						if f := update.OutputFolder; f.Present {
							job.OutputFolder.Set(f.Value)
						}

						if a := update.NewTimeAllocation; a.Present {
							job.TimeAllocation.Set(orcapi.SimpleDurationFromMillis(update.NewTimeAllocation.Value))
						}

						job.Updates = append(job.Updates, update)
					}
				}
			})

			if !ok {
				return util.Empty{}, util.HttpErr(http.StatusNotFound, "unknown job or permission denied (%v)", jobId)
			}
		}

		return util.Empty{}, nil
	})

	orcapi.JobsBrowse.Handler(func(info rpc.RequestInfo, request orcapi.JobsBrowseRequest) (fndapi.PageV2[orcapi.Job], *util.HttpError) {
		return JobsBrowse(info.Actor, request.Next, request.ItemsPerPage, request.JobFlags)
	})

	orcapi.JobsControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.JobsControlBrowseRequest) (fndapi.PageV2[orcapi.Job], *util.HttpError) {
		return JobsBrowse(info.Actor, request.Next, request.ItemsPerPage, request.JobFlags)
	})

	orcapi.JobsRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.JobsRetrieveRequest) (orcapi.Job, *util.HttpError) {
		return JobsRetrieve(info.Actor, request.Id, request.JobFlags)
	})

	orcapi.JobsControlRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.JobsControlRetrieveRequest) (orcapi.Job, *util.HttpError) {
		return JobsRetrieve(info.Actor, request.Id, request.JobFlags)
	})

	orcapi.JobsRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.JobSupport], *util.HttpError) {
		return SupportRetrieveProducts[orcapi.JobSupport](jobType), nil
	})

	orcapi.JobsSearch.Handler(func(info rpc.RequestInfo, request orcapi.JobsSearchRequest) (fndapi.PageV2[orcapi.Job], *util.HttpError) {
		return JobsSearch(info.Actor, request.Query, request.Next, request.ItemsPerPage, request.JobFlags)
	})

	orcapi.JobsControlRegister.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ProviderRegisteredResource[orcapi.JobSpecification]]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		var responses []fndapi.FindByStringId

		providerId, _ := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		for _, reqItem := range request.Items {
			if reqItem.Spec.Product.Provider != providerId {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(http.StatusForbidden, "forbidden")
			}
		}

		for _, reqItem := range request.Items {
			var flags resourceCreateFlags
			if reqItem.ProjectAllRead {
				flags |= resourceCreateAllRead
			}

			if reqItem.ProjectAllWrite {
				flags |= resourceCreateAllWrite
			}

			spec := reqItem.Spec

			support, _ := SupportByProduct[orcapi.JobSupport](jobType, spec.Product)

			encodedParams, _ := json.Marshal(spec.Parameters)
			encodedResources, _ := json.Marshal(spec.Resources)
			encodedProduct, _ := json.Marshal(support.Product)
			encodedSupport, _ := json.Marshal(support.ResolvedSupport)
			encodedMachineType, _ := json.Marshal(map[string]any{
				"cpu":          support.Product.Cpu,
				"memoryInGigs": support.Product.MemoryInGigs,
			})

			id, _, err := ResourceCreateEx[orcapi.Job](
				jobType,
				orcapi.ResourceOwner{
					CreatedBy: reqItem.CreatedBy.GetOrDefault("_ucloud"),
					Project:   reqItem.Project.Value,
				},
				nil,
				util.OptValue(reqItem.Spec.Product),
				reqItem.ProviderGeneratedId,
				&internalJob{
					Application:    spec.Application,
					Name:           spec.Name,
					Replicas:       spec.Replicas,
					Parameters:     spec.Parameters,
					Resources:      spec.Resources,
					TimeAllocation: spec.TimeAllocation,
					OpenedFile:     spec.OpenedFile,
					SshEnabled:     spec.SshEnabled,
					State:          orcapi.JobStateInQueue,
					JobParametersJson: orcapi.ExportedParameters{
						SiteVersion: 3,
						Request: orcapi.ExportedParametersRequest{
							Application:       spec.Application,
							Product:           spec.Product,
							Name:              spec.Name,
							Replicas:          spec.Replicas,
							Parameters:        encodedParams,
							Resources:         encodedResources,
							TimeAllocation:    spec.TimeAllocation.GetOrDefault(orcapi.SimpleDuration{}),
							ResolvedProduct:   encodedProduct,
							ResolvedSupport:   encodedSupport,
							AllowDuplicateJob: false,
							SshEnabled:        spec.SshEnabled,
						},
						ResolvedResources: orcapi.ExportedParametersResources{
							// TODO
						},
						MachineType: encodedMachineType,
					},
					StartedAt: util.OptNone[fndapi.Timestamp](),
					Updates:   nil,
				},
				flags,
			)

			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			} else {
				ResourceConfirm(jobType, id)
				responses = append(responses, fndapi.FindByStringId{Id: fmt.Sprint(id)})
			}
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})

	orcapi.JobsUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.UpdatedAcl]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		var responses []util.Empty
		for _, item := range request.Items {
			err := ResourceUpdateAcl(info.Actor, jobType, item)
			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
			responses = append(responses, util.Empty{})
		}
		return fndapi.BulkResponse[util.Empty]{Responses: responses}, nil
	})

	// TODO Feature checks

	orcapi.JobsExtend.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.JobsExtendRequestItem]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		updatesByProvider := map[string][]orcapi.JobsProviderExtendRequestItem{}

		for _, item := range request.Items {
			resc, _, _, err := ResourceRetrieveEx[orcapi.Job](
				info.Actor,
				jobType,
				ResourceParseId(item.JobId),
				orcapi.PermissionEdit,
				orcapi.ResourceFlags{IncludeProduct: true},
			)

			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, util.HttpErr(http.StatusNotFound, "permission denied or job not found (%v)", item.JobId)
			}

			provider := resc.Specification.Product.Provider
			updatesByProvider[provider] = append(updatesByProvider[provider], orcapi.JobsProviderExtendRequestItem{
				Job:           resc,
				RequestedTime: item.RequestedTime,
			})
		}

		for provider, requests := range updatesByProvider {
			_, err := InvokeProvider(
				provider,
				orcapi.JobsProviderExtend,
				fndapi.BulkRequestOf(requests...),
				ProviderCallOpts{
					Username: util.OptValue(info.Actor.Username),
					Reason:   util.OptValue("user initiated extension"),
				},
			)

			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.JobsTerminate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		updatesByProvider := map[string][]orcapi.Job{}

		for _, item := range request.Items {
			resc, _, _, err := ResourceRetrieveEx[orcapi.Job](
				info.Actor,
				jobType,
				ResourceParseId(item.Id),
				orcapi.PermissionEdit,
				orcapi.ResourceFlags{IncludeProduct: true},
			)

			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, util.HttpErr(http.StatusNotFound, "permission denied or job not found (%v)", item.Id)
			}

			provider := resc.Specification.Product.Provider
			updatesByProvider[provider] = append(updatesByProvider[provider], resc)
		}

		for provider, requests := range updatesByProvider {
			_, err := InvokeProvider(
				provider,
				orcapi.JobsProviderTerminate,
				fndapi.BulkRequestOf(requests...),
				ProviderCallOpts{
					Username: util.OptValue(info.Actor.Username),
					Reason:   util.OptValue("user initiated termination"),
				},
			)

			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.JobsSuspend.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		updatesByProvider := map[string][]orcapi.JobsProviderSuspendRequestItem{}

		for _, item := range request.Items {
			resc, _, _, err := ResourceRetrieveEx[orcapi.Job](
				info.Actor,
				jobType,
				ResourceParseId(item.Id),
				orcapi.PermissionEdit,
				orcapi.ResourceFlags{IncludeProduct: true},
			)

			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, util.HttpErr(http.StatusNotFound, "permission denied or job not found (%v)", item.Id)
			}

			provider := resc.Specification.Product.Provider
			updatesByProvider[provider] = append(updatesByProvider[provider], orcapi.JobsProviderSuspendRequestItem{Job: resc})
		}

		for provider, requests := range updatesByProvider {
			_, err := InvokeProvider(
				provider,
				orcapi.JobsProviderSuspend,
				fndapi.BulkRequestOf(requests...),
				ProviderCallOpts{
					Username: util.OptValue(info.Actor.Username),
					Reason:   util.OptValue("user initiated suspension"),
				},
			)

			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.JobsUnsuspend.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		updatesByProvider := map[string][]orcapi.JobsProviderUnsuspendRequestItem{}

		for _, item := range request.Items {
			resc, _, _, err := ResourceRetrieveEx[orcapi.Job](
				info.Actor,
				jobType,
				ResourceParseId(item.Id),
				orcapi.PermissionEdit,
				orcapi.ResourceFlags{IncludeProduct: true},
			)

			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, util.HttpErr(http.StatusNotFound, "permission denied or job not found (%v)", item.Id)
			}

			provider := resc.Specification.Product.Provider
			updatesByProvider[provider] = append(updatesByProvider[provider], orcapi.JobsProviderUnsuspendRequestItem{Job: resc})
		}

		for provider, requests := range updatesByProvider {
			_, err := InvokeProvider(
				provider,
				orcapi.JobsProviderUnsuspend,
				fndapi.BulkRequestOf(requests...),
				ProviderCallOpts{
					Username: util.OptValue(info.Actor.Username),
					Reason:   util.OptValue("user initiated suspension"),
				},
			)

			if err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
		}

		return fndapi.BulkResponse[util.Empty]{Responses: make([]util.Empty, len(request.Items))}, nil
	})

	orcapi.JobsOpenInteractiveSession.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.JobsOpenInteractiveSessionRequestItem]) (fndapi.BulkResponse[orcapi.OpenSessionWithProvider], *util.HttpError) {
		updatesByProvider := map[string][]orcapi.JobsProviderOpenInteractiveSessionRequestItem{}
		indicesByProvider := map[string][]int{}

		for index, item := range request.Items {
			resc, _, _, err := ResourceRetrieveEx[orcapi.Job](
				info.Actor,
				jobType,
				ResourceParseId(item.Id),
				orcapi.PermissionEdit,
				orcapi.ResourceFlags{IncludeProduct: true},
			)

			if err != nil {
				return fndapi.BulkResponse[orcapi.OpenSessionWithProvider]{}, util.HttpErr(http.StatusNotFound, "permission denied or job not found (%v)", item.Id)
			}

			provider := resc.Specification.Product.Provider
			updatesByProvider[provider] = append(updatesByProvider[provider], orcapi.JobsProviderOpenInteractiveSessionRequestItem{
				Job:         resc,
				Rank:        item.Rank,
				SessionType: item.SessionType,
				Target:      item.Target,
			})
			indicesByProvider[provider] = append(indicesByProvider[provider], index)
		}

		responses := make([]orcapi.OpenSessionWithProvider, len(request.Items))

		for provider, requests := range updatesByProvider {
			resp, err := InvokeProvider(
				provider,
				orcapi.JobsProviderOpenInteractiveSession,
				fndapi.BulkRequestOf(requests...),
				ProviderCallOpts{
					Username: util.OptValue(info.Actor.Username),
					Reason:   util.OptValue("user initiated suspension"),
				},
			)

			if err != nil {
				return fndapi.BulkResponse[orcapi.OpenSessionWithProvider]{}, err
			}

			if len(resp.Responses) != len(requests) {
				return fndapi.BulkResponse[orcapi.OpenSessionWithProvider]{}, util.HttpErr(http.StatusBadGateway, "bad response from provider")
			}

			domain, _ := ProviderDomain(provider)
			providerIndices := indicesByProvider[provider]

			for providerIdx, _ := range requests {
				mappedIdx := providerIndices[providerIdx]
				responses[mappedIdx] = orcapi.OpenSessionWithProvider{
					ProviderDomain: domain,
					ProviderId:     provider,
					Session:        resp.Responses[providerIdx],
				}

				if resp.Responses[providerIdx].DomainOverride != "" {
					responses[mappedIdx].ProviderDomain = resp.Responses[providerIdx].DomainOverride
				}
			}
		}

		return fndapi.BulkResponse[orcapi.OpenSessionWithProvider]{Responses: responses}, nil
	})

	orcapi.JobsRequestDynamicParameters.Handler(func(info rpc.RequestInfo, request orcapi.JobsRequestDynamicParametersRequest) (orcapi.JobsRequestDynamicParametersResponse, *util.HttpError) {
		app, ok := AppRetrieve(info.Actor, request.Application.Name, request.Application.Version, AppDiscoveryAll, 0)
		if !ok {
			return orcapi.JobsRequestDynamicParametersResponse{}, util.HttpErr(http.StatusNotFound, "unknown application")
		}

		providers, err := accapi.FindRelevantProviders.Invoke(fndapi.BulkRequestOf(accapi.FindRelevantProvidersRequest{
			Username:          info.Actor.Username,
			Project:           util.OptStringIfNotEmpty(string(info.Actor.Project.Value)),
			UseProject:        info.Actor.Project.Present,
			FilterProductType: util.OptValue(accapi.ProductTypeCompute),
			IncludeFreeToUse:  util.OptValue(false),
		}))

		if err != nil || len(providers.Responses) != 1 {
			return orcapi.JobsRequestDynamicParametersResponse{}, util.HttpErr(http.StatusInternalServerError, "internal server error")
		}

		response := map[string][]orcapi.ApplicationParameter{}

		for _, provider := range providers.Responses[0].Providers {
			resp, err := InvokeProvider(
				provider,
				orcapi.JobsProviderRequestDynamicParameters,
				orcapi.JobsProviderRequestDynamicParametersRequest{
					Owner: orcapi.ResourceOwner{
						CreatedBy: info.Actor.Username,
						Project:   string(info.Actor.Project.Value),
					},
					Application: app,
				},
				ProviderCallOpts{
					Username: util.OptValue(info.Actor.Username),
					Reason:   util.OptValue("user initiated request"),
				},
			)

			if err != nil {
				return orcapi.JobsRequestDynamicParametersResponse{}, err
			}

			response[provider] = resp.Parameters
		}

		return orcapi.JobsRequestDynamicParametersResponse{ParametersByProvider: response}, nil
	})

	orcapi.JobsOpenTerminalInFolder.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.JobsOpenTerminalInFolderRequestItem]) (fndapi.BulkResponse[orcapi.OpenSessionWithProvider], *util.HttpError) {
		updatesByProvider := map[string][]orcapi.JobsOpenTerminalInFolderRequestItem{}
		indicesByProvider := map[string][]int{}

		for index, item := range request.Items {
			driveId, ok := orcapi.DriveIdFromUCloudPath(item.Folder)
			if !ok {
				return fndapi.BulkResponse[orcapi.OpenSessionWithProvider]{}, util.HttpErr(http.StatusBadRequest, "bad folder path supplied")
			}

			drive, _, _, err := ResourceRetrieveEx[orcapi.Drive](info.Actor, driveType, ResourceParseId(driveId), orcapi.PermissionEdit, orcapi.ResourceFlags{})
			if err != nil {
				return fndapi.BulkResponse[orcapi.OpenSessionWithProvider]{}, util.HttpErr(http.StatusForbidden, "permission denied")
			}

			provider := drive.Specification.Product.Provider

			updatesByProvider[provider] = append(updatesByProvider[provider], item)
			indicesByProvider[provider] = append(indicesByProvider[provider], index)
		}

		responses := make([]orcapi.OpenSessionWithProvider, len(request.Items))

		for provider, requests := range updatesByProvider {
			resp, err := InvokeProvider(
				provider,
				orcapi.JobsProviderOpenTerminalInFolder,
				fndapi.BulkRequestOf(requests...),
				ProviderCallOpts{
					Username: util.OptValue(info.Actor.Username),
					Reason:   util.OptValue("user initiated request"),
				},
			)

			if err != nil {
				return fndapi.BulkResponse[orcapi.OpenSessionWithProvider]{}, err
			}

			if len(resp.Responses) != len(requests) {
				return fndapi.BulkResponse[orcapi.OpenSessionWithProvider]{}, util.HttpErr(http.StatusBadGateway, "bad response from provider")
			}

			domain, _ := ProviderDomain(provider)
			providerIndices := indicesByProvider[provider]

			for providerIdx, _ := range requests {
				mappedIdx := providerIndices[providerIdx]
				responses[mappedIdx] = orcapi.OpenSessionWithProvider{
					ProviderDomain: domain,
					ProviderId:     provider,
					Session:        resp.Responses[providerIdx],
				}

				if resp.Responses[providerIdx].DomainOverride != "" {
					responses[mappedIdx].ProviderDomain = resp.Responses[providerIdx].DomainOverride
				}
			}
		}

		return fndapi.BulkResponse[orcapi.OpenSessionWithProvider]{Responses: responses}, nil
	})

	orcapi.JobsRename.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.JobRenameRequest]) (util.Empty, *util.HttpError) {
		for _, item := range request.Items {
			ResourceUpdate(
				info.Actor,
				jobType,
				ResourceParseId(item.Id),
				orcapi.PermissionEdit,
				func(r *resource, mapped orcapi.Job) {
					job := r.Extra.(*internalJob)
					job.Name = item.NewTitle
				},
			)
		}
		return util.Empty{}, nil
	})

	wsUpgrader := ws.Upgrader{
		ReadBufferSize:  1024 * 4,
		WriteBufferSize: 1024 * 4,
		Subprotocols:    []string{"binary"},
	}
	wsUpgrader.CheckOrigin = func(r *http.Request) bool { return true }

	followCall := rpc.Call[util.Empty, util.Empty]{
		BaseContext: "jobs",
		Convention:  rpc.ConventionWebSocket,
	}

	followCall.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		conn := info.WebSocket
		jobsFollow(conn)
		util.SilentClose(conn)

		return util.Empty{}, nil
	})
}

func jobBindResource(jobId string, resc orcapi.AppParameterValue) {
	switch resc.Type {
	case orcapi.AppParameterValueTypeNetwork:
		PublicIpBind(resc.Id, jobId)
	case orcapi.AppParameterValueTypeIngress:
		IngressBind(resc.Id, jobId)
	case orcapi.AppParameterValueTypeLicense:
		LicenseBind(resc.Id, jobId)
	}
}

func jobUnbindResource(jobId string, resc orcapi.AppParameterValue) {
	switch resc.Type {
	case orcapi.AppParameterValueTypeNetwork:
		PublicIpUnbind(resc.Id, jobId)
	case orcapi.AppParameterValueTypeIngress:
		IngressUnbind(resc.Id, jobId)
	case orcapi.AppParameterValueTypeLicense:
		LicenseUnbind(resc.Id, jobId)
	}
}

func jobsFollow(conn *ws.Conn) {
	var initialJob orcapi.Job
	var actor rpc.Actor
	var streamId string

	connMutex := sync.Mutex{}
	sendToClient := func(data orcapi.JobsFollowMessage) bool {
		data.Updates = util.NonNilSlice(data.Updates)
		data.Log = util.NonNilSlice(data.Log)
		dataBytes := rpc.WSResponseMessageMarshal(streamId, data)

		connMutex.Lock()
		err := conn.WriteMessage(ws.TextMessage, dataBytes)
		connMutex.Unlock()

		return err == nil
	}

	retrieveFlags := orcapi.ResourceFlags{
		IncludeProduct: true,
		IncludeSupport: true,
		IncludeOthers:  true,
		IncludeUpdates: true,
	}

	{
		// Read and authenticate request
		// -------------------------------------------------------------------------------------------------------------

		var herr *util.HttpError

		mtype, rawMessage, err := conn.ReadMessage()
		if err != nil || mtype != ws.TextMessage {
			return
		}

		var message rpc.WSRequestMessage[fndapi.FindByStringId]

		err = json.Unmarshal(rawMessage, &message)
		if err != nil {
			return
		}

		streamId = message.StreamId

		actor, herr = rpc.BearerAuthenticator(message.Bearer, message.Project.GetOrDefault(""))
		if herr != nil {
			return
		}

		initialJob, herr = ResourceRetrieve[orcapi.Job](actor, jobType, ResourceParseId(message.Payload.Id), retrieveFlags)
		if herr != nil {
			return
		}
	}

	providerId := initialJob.Specification.Product.Provider

	sendToClient(orcapi.JobsFollowMessage{
		InitialJob: util.OptValue(initialJob),
	})

	wg := sync.WaitGroup{}
	wg.Add(2)

	keepRunning := atomic.Bool{}
	keepRunning.Store(true)

	// Start job watcher
	go func() {
		nextLoad := 0

		updatesSeen := len(initialJob.Updates)
		prevStatus := initialJob.Status

		for keepRunning.Load() {
			if nextLoad <= 0 {
				job, herr := ResourceRetrieve[orcapi.Job](actor, jobType, ResourceParseId(initialJob.Id), retrieveFlags)
				if herr != nil {
					break
				}

				var newUpdates []orcapi.JobUpdate
				var newStatus util.Option[orcapi.JobStatus]

				if len(job.Updates) > updatesSeen {
					newUpdates = job.Updates[updatesSeen:]
					updatesSeen = len(job.Updates)
				}

				if job.Status.State != prevStatus.State {
					newStatus.Set(job.Status)
					prevStatus = job.Status
				}

				if len(newUpdates) != 0 || newStatus.Present {
					ok := sendToClient(orcapi.JobsFollowMessage{
						Updates:   newUpdates,
						NewStatus: newStatus,
					})

					if !ok {
						break
					}
				}

				nextLoad = 10
			}

			time.Sleep(50 * time.Millisecond)
			nextLoad--
		}

		wg.Done()
		keepRunning.Store(false)
	}()

	// Start provider watcher
	go func() {
		// Check for support
		// -------------------------------------------------------------------------------------------------------------
		hasSupport := false
		support, ok := SupportByProduct[orcapi.JobSupport](jobType, initialJob.Specification.Product)
		if ok {
			appNv := initialJob.Specification.Application
			app, ok := AppRetrieve(actor, appNv.Name, appNv.Version, AppDiscoveryAll, 0)
			if ok {
				key := jobDockerLogs
				backend := app.Invocation.Tool.Tool.Value.Description.Backend
				switch backend {
				case orcapi.ToolBackendNative:
					key = jobNativeLogs
				case orcapi.ToolBackendDocker:
					key = jobDockerLogs
				case orcapi.ToolBackendVirtualMachine:
					key = jobVmLogs
				}

				hasSupport = support.Has(key)
			}
		}

		if !hasSupport {
			wg.Done()
			// Do not set keep running in this case
			return
		}

		// Contact provider and process logs
		// -------------------------------------------------------------------------------------------------------------
		client, ok := providerClient(providerId)
		if ok {
			url := strings.ReplaceAll(client.BasePath, "http://", "")
			url = strings.ReplaceAll(url, "https://", "")
			url = fmt.Sprintf(
				"ws://%s%s?usernameHint=%s",
				url,
				orcapi.JobsProviderFollowEndpoint(providerId),
				base64.URLEncoding.EncodeToString([]byte(actor.Username)),
			)

			providerConn, _, err := ws.DefaultDialer.Dial(url, http.Header{
				"Authorization": []string{fmt.Sprintf("Bearer %s", client.RetrieveAccessTokenOrRefresh())},
			})
			if err == nil {
				dataBytes, _ := json.Marshal(rpc.WSRequestMessage[orcapi.JobsProviderFollowRequest]{
					Call:     fmt.Sprintf("jobs.provider.%s.follow", providerId),
					StreamId: "ignored",
					Payload: orcapi.JobsProviderFollowRequest{
						Type: "init",
						Job:  initialJob,
					},
				})

				err = providerConn.WriteMessage(ws.TextMessage, dataBytes)
			}

			if err == nil {
				for keepRunning.Load() {
					mtype, rawMessage, err := providerConn.ReadMessage()
					if err != nil || mtype != ws.TextMessage {
						break
					}

					var message rpc.WSResponseMessage[orcapi.JobLogMessage]
					_ = json.Unmarshal(rawMessage, &message)

					ok = sendToClient(orcapi.JobsFollowMessage{
						Log: []orcapi.JobLogMessage{message.Payload},
					})

					if !ok {
						break
					}
				}

				util.SilentClose(providerConn)
			}
		}

		wg.Done()
		keepRunning.Store(false)
	}()

	wg.Wait()
}

func jobsValidateForSubmission(actor rpc.Actor, spec *orcapi.JobSpecification) *util.HttpError {
	var err *util.HttpError

	app, ok := AppRetrieve(actor, spec.Application.Name, spec.Application.Version, AppDiscoveryAll, 0)
	if !ok {
		return util.HttpErr(http.StatusBadRequest, "unknown application requested")
	}

	support, ok := SupportByProduct[orcapi.JobSupport](jobType, spec.Product)
	if !ok {
		return util.HttpErr(http.StatusBadRequest, "bad machine type requested")
	}

	toolSupported := false
	tool := app.Invocation.Tool.Tool.Value.Description
	switch tool.Backend {
	case orcapi.ToolBackendDocker:
		toolSupported = support.Has(jobDockerEnabled)
	case orcapi.ToolBackendVirtualMachine:
		toolSupported = support.Has(jobVmEnabled)
	case orcapi.ToolBackendNative:
		toolSupported = support.Has(jobNativeEnabled)
	}

	if !toolSupported {
		return util.HttpErr(http.StatusBadRequest, "the application is not supported on this machine type")
	}

	if spec.TimeAllocation.Present {
		if tool.Backend == orcapi.ToolBackendVirtualMachine {
			spec.TimeAllocation.Clear()
		} else if spec.TimeAllocation.Value.ToMillis() <= 0 {
			return util.HttpErr(http.StatusBadRequest, "time allocated for job is too short")
		}
	}

	sshMode := util.EnumOrDefault(app.Invocation.Ssh.Mode, orcapi.SshModeOptions, orcapi.SshModeDisabled)
	if spec.SshEnabled && sshMode == orcapi.SshModeDisabled {
		return util.HttpErr(http.StatusBadRequest, "this application does not support SSH but it is required")
	}

	if spec.Replicas <= 0 {
		return util.HttpErr(http.StatusBadRequest, "you must request at least 1 node")
	}

	util.ValidateString(&spec.Name, "name", util.StringValidationAllowEmpty, &err)
	if err != nil {
		return err
	}

	appParamsByName := map[string]orcapi.ApplicationParameter{}
	for _, param := range app.Invocation.Parameters {
		appParamsByName[param.Name] = param
	}

	for i, value := range spec.Resources {
		newValue := value
		err := jobValidateValue(actor, &newValue)
		if err != nil {
			return err
		} else {
			spec.Resources[i] = newValue
		}
	}

	for name, value := range spec.Parameters {
		newValue := value
		err := jobValidateValue(actor, &newValue)
		if err != nil {
			return err
		} else {
			spec.Parameters[name] = newValue
		}

		if !strings.HasPrefix(name, "_injected_") {
			param, ok := appParamsByName[name]
			if !ok {
				return util.HttpErr(http.StatusBadRequest, "unknown parameter supplied: '%s'", name)
			}
			switch param.Type {
			case orcapi.ApplicationParameterTypeInputFile, orcapi.ApplicationParameterTypeInputDirectory:
				if value.Type != orcapi.AppParameterValueTypeFile {
					return util.HttpErr(http.StatusBadRequest, "incorrect parameter type for '%s'", name)
				}

			case orcapi.ApplicationParameterTypeText, orcapi.ApplicationParameterTypeTextArea, orcapi.ApplicationParameterTypeEnumeration:
				if value.Type != orcapi.AppParameterValueTypeText {
					return util.HttpErr(http.StatusBadRequest, "incorrect parameter type for '%s'", name)
				}

			case orcapi.ApplicationParameterTypeInteger:
				if value.Type != orcapi.AppParameterValueTypeInteger {
					return util.HttpErr(http.StatusBadRequest, "incorrect parameter type for '%s'", name)
				}

			case orcapi.ApplicationParameterTypeBoolean:
				if value.Type != orcapi.AppParameterValueTypeBoolean {
					return util.HttpErr(http.StatusBadRequest, "incorrect parameter type for '%s'", name)
				}

			case orcapi.ApplicationParameterTypeFloatingPoint:
				if value.Type != orcapi.AppParameterValueTypeFloatingPoint {
					return util.HttpErr(http.StatusBadRequest, "incorrect parameter type for '%s'", name)
				}

			case orcapi.ApplicationParameterTypePeer:
				if value.Type != orcapi.AppParameterValueTypePeer {
					return util.HttpErr(http.StatusBadRequest, "incorrect parameter type for '%s'", name)
				}

			case orcapi.ApplicationParameterTypeLicenseServer:
				if value.Type != orcapi.AppParameterValueTypeLicense {
					return util.HttpErr(http.StatusBadRequest, "incorrect parameter type for '%s'", name)
				}

			case orcapi.ApplicationParameterTypeIngress:
				if value.Type != orcapi.AppParameterValueTypeIngress {
					return util.HttpErr(http.StatusBadRequest, "incorrect parameter type for '%s'", name)
				}

			case orcapi.ApplicationParameterTypeNetworkIp:
				if value.Type != orcapi.AppParameterValueTypeNetwork {
					return util.HttpErr(http.StatusBadRequest, "incorrect parameter type for '%s'", name)
				}

			case orcapi.ApplicationParameterTypeWorkflow:
				if value.Type != orcapi.AppParameterValueTypeWorkflow {
					return util.HttpErr(http.StatusBadRequest, "incorrect parameter type for '%s'", name)
				}
			case orcapi.ApplicationParameterTypeReadme:
				return util.HttpErr(http.StatusBadRequest, "incorrect parameter type for '%s'", name)

			case orcapi.ApplicationParameterTypeModuleList:
				if value.Type != orcapi.AppParameterValueTypeModuleList {
					return util.HttpErr(http.StatusBadRequest, "incorrect parameter type for '%s'", name)
				}
			}
		}
	}

	for name, param := range appParamsByName {
		if !param.Optional && (param.DefaultValue == nil || string(param.DefaultValue) == "null") {
			_, ok := spec.Parameters[name]
			if !ok {
				return util.HttpErr(http.StatusBadRequest, "missing value for '%s'", name)
			}
		}
	}

	return nil
}

func jobValidateValue(actor rpc.Actor, value *orcapi.AppParameterValue) *util.HttpError {
	switch value.Type {
	case orcapi.AppParameterValueTypeFile:
		path := value.Path
		driveId, ok := orcapi.DriveIdFromUCloudPath(path)
		if !ok {
			return util.HttpErr(http.StatusBadRequest, "bad file requested at '%s'", path)
		}

		_, resc, _, err := ResourceRetrieveEx[orcapi.Drive](actor, driveType, ResourceParseId(driveId),
			orcapi.PermissionRead, orcapi.ResourceFlags{})
		if err != nil {
			return util.HttpErr(http.StatusBadRequest, "unknown file or permission denied at '%s'", path)
		}

		value.ReadOnly = !orcapi.PermissionsHas(resc.Permissions.Myself, orcapi.PermissionEdit)
		return nil

	case orcapi.AppParameterValueTypePeer:
		if err := util.ValidateStringE(&value.Hostname, "hostname", 0); err != nil {
			return err
		}

		jobId := value.JobId
		job, _, _, err := ResourceRetrieveEx[orcapi.Job](actor, jobType, ResourceParseId(jobId),
			orcapi.PermissionEdit, orcapi.ResourceFlags{})

		if err != nil {
			return util.HttpErr(http.StatusBadRequest, "job with hostname '%s' is not valid", value.Hostname)
		}

		if job.Status.State != orcapi.JobStateRunning {
			return util.HttpErr(http.StatusBadRequest, "job with hostname '%s' is not running", value.Hostname)
		}

	case orcapi.AppParameterValueTypeNetwork:
		resc, _, _, err := ResourceRetrieveEx[orcapi.PublicIp](actor, publicIpType, ResourceParseId(value.Id),
			orcapi.PermissionEdit, orcapi.ResourceFlags{})

		if err != nil {
			return util.HttpErr(http.StatusForbidden, "you cannot use this IP address")
		}

		if len(resc.Status.BoundTo) > 0 {
			return util.HttpErr(http.StatusForbidden, "this IP is already in use with %v", resc.Status.BoundTo[0])
		}

	case orcapi.AppParameterValueTypeIngress:
		resc, _, _, err := ResourceRetrieveEx[orcapi.Ingress](actor, ingressType, ResourceParseId(value.Id),
			orcapi.PermissionEdit, orcapi.ResourceFlags{})

		if err != nil {
			return util.HttpErr(http.StatusForbidden, "you cannot use this link")
		}

		if len(resc.Status.BoundTo) > 0 {
			return util.HttpErr(http.StatusForbidden, "this link is already in use with %v", resc.Status.BoundTo[0])
		}

	case orcapi.AppParameterValueTypeLicense:
		_, _, _, err := ResourceRetrieveEx[orcapi.License](actor, licenseType, ResourceParseId(value.Id),
			orcapi.PermissionEdit, orcapi.ResourceFlags{})

		if err != nil {
			return util.HttpErr(http.StatusForbidden, "you cannot use this license")
		}
	}

	return nil
}

func JobsSearch(
	actor rpc.Actor,
	query string,
	next util.Option[string],
	itemsPerPage int,
	flags orcapi.JobFlags,
) (fndapi.PageV2[orcapi.Job], *util.HttpError) {
	return ResourceBrowse(
		actor,
		jobType,
		next,
		itemsPerPage,
		flags.ResourceFlags,
		func(item orcapi.Job) bool {
			if app := flags.FilterApplication; app.Present && app.Value != item.Specification.Application.Name {
				return false
			} else if state := flags.FilterState; state.Present && state.Value != item.Status.State {
				return false
			}

			query = strings.ToLower(query)
			if query == "" {
				return true
			} else if strings.Contains(strings.ToLower(item.Specification.Application.Name), query) {
				return true
			} else if strings.Contains(strings.ToLower(item.Specification.Name), query) {
				return true
			} else if strings.Contains(strings.ToLower(item.Id), query) {
				return true
			}

			return false
		},
		nil,
	), nil
}

func JobsBrowse(
	actor rpc.Actor,
	next util.Option[string],
	itemsPerPage int,
	flags orcapi.JobFlags,
) (fndapi.PageV2[orcapi.Job], *util.HttpError) {
	sortByFn := ResourceDefaultComparator(func(item orcapi.Job) orcapi.Resource {
		return item.Resource
	}, flags.ResourceFlags)

	return ResourceBrowse(
		actor,
		jobType,
		next,
		itemsPerPage,
		flags.ResourceFlags,
		func(item orcapi.Job) bool {
			if app := flags.FilterApplication; app.Present && app.Value != item.Specification.Application.Name {
				return false
			} else if state := flags.FilterState; state.Present && state.Value != item.Status.State {
				return false
			}

			return true
		},
		sortByFn,
	), nil
}

func JobsRetrieve(actor rpc.Actor, id string, flags orcapi.JobFlags) (orcapi.Job, *util.HttpError) {
	return ResourceRetrieve[orcapi.Job](actor, jobType, ResourceParseId(id), flags.ResourceFlags)
}

type internalJobChangeFlag uint64

const (
	internalJobPartialChange internalJobChangeFlag = 1 << iota
	internalJobChangeMetadata
	internalJobChangeResources
	internalJobChangeParameters
	internalJobChangeUpdates
)

type internalJob struct {
	Application    orcapi.NameAndVersion
	Name           string
	Replicas       int
	Parameters     map[string]orcapi.AppParameterValue
	Resources      []orcapi.AppParameterValue
	TimeAllocation util.Option[orcapi.SimpleDuration]
	OpenedFile     string
	SshEnabled     bool
	OutputFolder   util.Option[string]

	State             orcapi.JobState
	JobParametersJson orcapi.ExportedParameters
	StartedAt         util.Option[fndapi.Timestamp]
	Updates           []orcapi.JobUpdate

	ChangeFlags       internalJobChangeFlag
	LastFlushedUpdate atomic.Uint64
}

func jobLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource             int64
		ApplicationName      string
		ApplicationVersion   string
		CurrentState         string
		TimeAllocationMillis sql.Null[int64]
		Replicas             int
		OutputFolder         sql.NullString
		Name                 sql.NullString
		StartedAt            sql.Null[time.Time]
		ExportedParameters   sql.NullString
		OpenedFile           sql.NullString
		SshEnabled           bool
		Parameters           string
		MountedResources     string
		Updates              string
	}](
		tx,
		`
			with
				inputs as (
					select 
						j.resource, 
						coalesce(
							jsonb_agg(jsonb_build_object('name', input.name, 'value', input.value))
								filter (where input.name is not null),
							cast('[]' as jsonb)
						) as parameters
					from
						app_orchestrator.jobs j
						left join app_orchestrator.job_input_parameters input on j.resource = input.job_id
					where
						j.resource = some(cast(:ids as int8[]))
					group by j.resource
				),
				mounts as (
					select 
						j.resource, 
						coalesce(
							jsonb_agg(input.resource) filter (where input.resource is not null), 
							cast('[]' as jsonb)
						) as mounted_resources
					from
						app_orchestrator.jobs j
						left join app_orchestrator.job_resources input on j.resource = input.job_id
					where
						j.resource = some(cast(:ids as int8[]))
					group by j.resource
				),
				updates as (
				    select
						j.resource,
						coalesce(
							jsonb_agg(
								jsonb_build_object(
									'timestamp', (floor(extract(epoch from u.created_at) * 1000)),
									'status', u.status
								) || u.extra
							) filter (where u.created_at is not null), 
							cast('[]' as jsonb)) as updates
				    from
				        app_orchestrator.jobs j
						left join provider.resource_update u on j.resource = u.resource
					where
						j.resource = some(cast(:ids as int8[]))
				    group by j.resource
				)
			select
				j.resource,
				j.application_name,
				j.application_version,
				j.current_state,
				j.time_allocation_millis,
				j.replicas,
				j.output_folder,
				j.name,
				j.started_at,
				j.job_parameters as exported_parameters,
				j.opened_file,
				j.ssh_enabled,
				i.parameters,
				m.mounted_resources,
				u.updates
			from
				app_orchestrator.jobs j
				join inputs i on i.resource = j.resource
				join mounts m on m.resource = j.resource
				join updates u on u.resource = j.resource
			where
				j.resource = some(cast(:ids as int8[]))
	    `,
		db.Params{
			"ids": ids,
		},
	)

	for _, row := range rows {
		r := resources[ResourceId(row.Resource)]
		info := &internalJob{
			Application: orcapi.NameAndVersion{
				Name:    row.ApplicationName,
				Version: row.ApplicationVersion,
			},
			Name:              row.Name.String,
			Replicas:          row.Replicas,
			OpenedFile:        row.OpenedFile.String,
			SshEnabled:        row.SshEnabled,
			State:             orcapi.JobState(row.CurrentState),
			JobParametersJson: orcapi.ExportedParameters{},
		}

		if row.StartedAt.Valid {
			info.StartedAt.Set(fndapi.Timestamp(row.StartedAt.V))
		}

		if row.TimeAllocationMillis.Valid {
			info.TimeAllocation.Set(orcapi.SimpleDurationFromMillis(row.TimeAllocationMillis.V))
		}

		{
			info.Parameters = map[string]orcapi.AppParameterValue{}

			var paramArray []struct {
				Name  string
				Value orcapi.AppParameterValue
			}

			_ = json.Unmarshal([]byte(row.Parameters), &paramArray)

			for _, elem := range paramArray {
				info.Parameters[elem.Name] = elem.Value
			}
		}

		_ = json.Unmarshal([]byte(row.MountedResources), &info.Resources)
		_ = json.Unmarshal([]byte(row.Updates), &info.Updates)

		if row.ExportedParameters.Valid {
			_ = json.Unmarshal([]byte(row.ExportedParameters.String), &info.JobParametersJson)
		}

		if row.OutputFolder.Valid {
			info.OutputFolder.Set(row.OutputFolder.String)
		}

		info.LastFlushedUpdate.Store(uint64(len(info.Updates)))
		r.Extra = info
	}
}

func jobPersist(b *db.Batch, r *resource) {
	if r.MarkedForDeletion {
		db.BatchExec(
			b,
			`
				delete from app_orchestrator.job_resources
				where job_id = :job_id
		    `,
			db.Params{
				"job_id": r.Id,
			},
		)

		db.BatchExec(
			b,
			`
				delete from app_orchestrator.job_input_parameters
				where job_id = :job_id
		    `,
			db.Params{
				"job_id": r.Id,
			},
		)

		db.BatchExec(
			b,
			`
				delete from app_orchestrator.jobs
				where resource = :job_id
		    `,
			db.Params{
				"job_id": r.Id,
			},
		)
	} else {
		info := r.Extra.(*internalJob)

		timeAlloc := sql.NullInt64{}
		if info.TimeAllocation.Present {
			timeAlloc.Valid = true
			timeAlloc.Int64 = info.TimeAllocation.Value.ToMillis()
		}

		startedAt := sql.NullTime{}
		if info.StartedAt.Present {
			startedAt.Valid = true
			startedAt.Time = info.StartedAt.Value.Time()
		}

		exportedParams, _ := json.Marshal(info.JobParametersJson)

		isPartial := info.ChangeFlags&internalJobPartialChange != 0

		if !isPartial || info.ChangeFlags&internalJobChangeMetadata != 0 {
			db.BatchExec(
				b,
				`
					insert into app_orchestrator.jobs (application_name, application_version, time_allocation_millis, 
						replicas, name, output_folder, current_state, started_at, resource, job_parameters, 
						opened_file, ssh_enabled)
					values (:app_name, :app_version, :time_alloc, :replicas, :name, :output_folder, :state, :started_at,
						:resource, :exported_params, :opened_file, :ssh_enabled)
					on conflict (resource) do update set
						application_name = excluded.application_name,
						application_version = excluded.application_version,
						time_allocation_millis = excluded.time_allocation_millis,
						replicas = excluded.replicas,
						name = excluded.name,
						output_folder = excluded.output_folder,
						current_state = excluded.current_state,
						started_at = excluded.started_at,
						resource = excluded.resource,
						job_parameters = excluded.job_parameters,
						opened_file = excluded.opened_file,
						ssh_enabled = excluded.ssh_enabled,
						last_update = now()
				`,
				db.Params{
					"app_name":        info.Application.Name,
					"app_version":     info.Application.Version,
					"time_alloc":      timeAlloc,
					"replicas":        info.Replicas,
					"name":            util.OptSqlStringIfNotEmpty(info.Name),
					"output_folder":   util.OptSqlStringIfNotEmpty(info.OutputFolder.GetOrDefault("")),
					"state":           info.State,
					"started_at":      startedAt,
					"resource":        r.Id,
					"exported_params": exportedParams,
					"opened_file":     util.OptSqlStringIfNotEmpty(info.OpenedFile),
					"ssh_enabled":     info.SshEnabled,
				},
			)
		}

		if !isPartial || info.ChangeFlags&internalJobChangeParameters != 0 {
			db.BatchExec(
				b,
				`
					delete from app_orchestrator.job_input_parameters
					where job_id = :job_id
				`,
				db.Params{
					"job_id": r.Id,
				},
			)

			for name, param := range info.Parameters {
				paramValue, _ := json.Marshal(param)

				db.BatchExec(
					b,
					`
						insert into app_orchestrator.job_input_parameters (name, value, job_id) 
						values (:name, :value, :job_id)
					`,
					db.Params{
						"job_id": r.Id,
						"name":   name,
						"value":  paramValue,
					},
				)
			}
		}

		if !isPartial || info.ChangeFlags&internalJobChangeResources != 0 {
			db.BatchExec(
				b,
				`
					delete from app_orchestrator.job_resources
					where job_id = :job_id
				`,
				db.Params{
					"job_id": r.Id,
				},
			)

			for _, param := range info.Resources {
				paramValue, _ := json.Marshal(param)

				db.BatchExec(
					b,
					`
						insert into app_orchestrator.job_resources(resource, job_id) 
						values (:value, :job_id)
					`,
					db.Params{
						"job_id": r.Id,
						"value":  paramValue,
					},
				)
			}
		}

		if !isPartial || info.ChangeFlags&internalJobChangeUpdates != 0 {
			lastUpdate := int(info.LastFlushedUpdate.Load())
			if !isPartial {
				db.BatchExec(
					b,
					`delete from provider.resource_update where resource = :job_id`,
					db.Params{
						"job_id": r.Id,
					},
				)
			}

			for i := lastUpdate; i < len(info.Updates); i++ {
				update := info.Updates[i]
				extra, _ := json.Marshal(update)

				db.BatchExec(
					b,
					`
						insert into provider.resource_update(resource, created_at, status, extra) 
						values (:job_id, :created_at, :status, :extra)
					`,
					db.Params{
						"job_id":     r.Id,
						"created_at": update.Timestamp.Time(),
						"status":     util.OptSqlStringIfNotEmpty(update.Status.GetOrDefault("")),
						"extra":      extra,
					},
				)
			}
		}
	}
}

func jobPersistCommitted(r *resource) {
	info := r.Extra.(*internalJob)
	info.LastFlushedUpdate.Store(uint64(len(info.Updates)))
	info.ChangeFlags = 0
}

func jobTransform(
	r orcapi.Resource,
	product util.Option[accapi.ProductReference],
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	info := extra.(*internalJob)

	result := orcapi.Job{
		Resource: r,
		Updates:  util.NonNilSlice(info.Updates),
		Specification: orcapi.JobSpecification{
			Product:        product.Value,
			Application:    info.Application,
			Name:           info.Name,
			Replicas:       info.Replicas,
			Parameters:     info.Parameters,
			Resources:      info.Resources,
			TimeAllocation: info.TimeAllocation,
			OpenedFile:     info.OpenedFile,
			SshEnabled:     info.SshEnabled,
		},
		Status: orcapi.JobStatus{
			State:               info.State,
			JobParametersJson:   info.JobParametersJson,
			StartedAt:           info.StartedAt,
			ResolvedApplication: orcapi.Application{},
		},
		Output: orcapi.JobOutput{
			OutputFolder: info.OutputFolder,
		},
	}

	if flags.IncludeProduct || flags.IncludeSupport {
		support, _ := SupportByProduct[orcapi.JobSupport](jobType, product.Value)
		result.Status.ResolvedProduct.Set(support.Product)
		result.Status.ResolvedSupport = support.ToApi()
	}

	if info.StartedAt.Present && info.TimeAllocation.Present {
		millis := time.Duration(info.TimeAllocation.Value.ToMillis()) * time.Millisecond
		result.Status.ExpiresAt.Set(fndapi.Timestamp(info.StartedAt.Value.Time().Add(millis)))
	}

	// TODO When to send this, we don't have the source flags?
	{
		app, _ := AppRetrieve(rpc.ActorSystem, info.Application.Name, info.Application.Version, AppDiscoveryAll, 0)
		result.Status.ResolvedApplication = app
	}

	return result
}

var jobNotificationsPending struct {
	Mu            sync.RWMutex
	EntriesByUser map[string]map[string]orcapi.Job // user -> job id -> job
}

func jobNotifyStateChange(job orcapi.Job) {
	if job.Status.State != orcapi.JobStateInQueue {
		jobNotificationsPending.Mu.Lock()
		username := job.Owner.CreatedBy
		current, ok := jobNotificationsPending.EntriesByUser[username]
		if !ok {
			current = map[string]orcapi.Job{}
			jobNotificationsPending.EntriesByUser[username] = current
		}
		current[job.Id] = job
		jobNotificationsPending.Mu.Unlock()
	}
}

func jobNotificationsLoopSendPending() {
	for {
		jobNotificationsPending.Mu.Lock()
		copiedEntriesByUser := map[string]map[string]orcapi.Job{}
		for username, jobs := range jobNotificationsPending.EntriesByUser {
			copiedEntriesByUser[username] = jobs
		}
		jobNotificationsPending.EntriesByUser = map[string]map[string]orcapi.Job{}
		jobNotificationsPending.Mu.Unlock()
		for username, jobs := range copiedEntriesByUser {
			jobSendNotifications(username, jobs)
		}
		time.Sleep(30 * time.Second)
	}
}

func mapStateToType(s orcapi.JobState) (string, bool) {
	switch s {
	case orcapi.JobStateSuccess:
		return "JOB_COMPLETED", true
	case orcapi.JobStateRunning:
		return "JOB_STARTED", true
	case orcapi.JobStateFailure:
		return "JOB_FAILED", true
	case orcapi.JobStateExpired:
		return "JOB_EXPIRED", true
	default:
		return "", false
	}
}

func jobSendNotifications(username string, jobs map[string]orcapi.Job) {
	groupedByState := map[orcapi.JobState][]orcapi.Job{}
	for _, job := range jobs {
		jobState := job.Status.State
		groupedByState[jobState] = append(groupedByState[jobState], job)
	}

	for state, group := range groupedByState {
		notificationType, ok := mapStateToType(state)
		if !ok {
			continue
		}

		var jobIds []string
		var appTitles []string
		var jobNames []string

		for _, job := range group {
			jobIds = append(jobIds, job.Id)
			appTitles = append(appTitles, job.Status.ResolvedApplication.Metadata.Title)
			jobNames = append(jobNames, job.Specification.Name)
		}

		meta := map[string]any{
			"jobIds":    jobIds,
			"appTitles": appTitles,
			"jobNames":  jobNames,
		}

		metaJson, _ := json.Marshal(meta)

		_, err := fndapi.NotificationsCreate.Invoke(fndapi.NotificationsCreateRequest{
			User: username,
			Notification: fndapi.Notification{
				Type: notificationType,
				Meta: util.OptValue(json.RawMessage(metaJson)),
			},
		})

		if err != nil {
			log.Warn("Could not send notification to user %s: %s", username, err)
		}
	}

	type mailEvent struct {
		JobName       string `json:"jobName"`
		AppName       string `json:"appName"`
		ChangeMessage string `json:"change"`
		JobId         string `json:"jobId"`
	}
	var mailTemplate struct {
		Type   fndapi.MailType `json:"type"`
		Events []mailEvent     `json:"events"`
	}

	for _, job := range jobs {
		event := mailEvent{
			JobName:       "",
			AppName:       job.Status.ResolvedApplication.Metadata.Title,
			ChangeMessage: "",
			JobId:         job.Id,
		}

		if job.Specification.Name != "" {
			event.JobName = job.Specification.Name
		} else {
			event.JobName = job.Id
		}

		switch job.Status.State {
		case orcapi.JobStateSuccess:
			event.ChangeMessage = "has finished running"
		case orcapi.JobStateExpired:
			event.ChangeMessage = "has expired"
		case orcapi.JobStateFailure:
			event.ChangeMessage = "has failed"
		case orcapi.JobStateRunning:
			event.ChangeMessage = "has started running"
		}

		mailTemplate.Events = append(mailTemplate.Events, event)
	}

	mailTemplate.Type = fndapi.MailTypeJobEvents
	mailBytes, _ := json.Marshal(mailTemplate)
	mail := fndapi.Mail(mailBytes)

	_, err := fndapi.MailSendToUser.Invoke(fndapi.BulkRequestOf(
		fndapi.MailSendToUserRequest{
			Receiver: username,
			Mail:     mail,
		}),
	)

	if err != nil {
		log.Warn("Could not send notification to user %s: %s", username, err)
	}
}
