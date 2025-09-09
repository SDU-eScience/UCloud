package orchestrator

import (
	"database/sql"
	"encoding/json"
	"net/http"
	"strings"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
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
	)

	orcapi.JobsCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.JobSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
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

			ids = append(ids, fndapi.FindByStringId{Id: job.Id})
		}

		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: ids}, nil
	})

	orcapi.JobsControlAddUpdate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.JobUpdate]]) (util.Empty, *util.HttpError) {
		updatesById := map[string][]orcapi.JobUpdate{}
		for _, item := range request.Items {
			updatesById[item.Id] = append(updatesById[item.Id], item.Update)
		}

		for jobId, updates := range updatesById {
			ok := ResourceUpdate(info.Actor, jobType, ResourceParseId(jobId), orcapi.PermissionProvider, func(r *resource, mapped orcapi.Job) {
				job := r.Extra.(*internalJob)

				for _, update := range updates {
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

							// TODO job notifications
							// TODO unbind resources
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
				return util.Empty{}, util.HttpErr(http.StatusNotFound, "unknown job or permission denied")
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
		// TODO Special handling for shares?
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

		jobId := value.Id
		job, _, _, err := ResourceRetrieveEx[orcapi.Job](actor, jobType, ResourceParseId(jobId),
			orcapi.PermissionEdit, orcapi.ResourceFlags{})

		if err != nil {
			return util.HttpErr(http.StatusBadRequest, "job with hostname '%s' is not valid", value.Hostname)
		}

		if job.Status.State != orcapi.JobStateRunning {
			return util.HttpErr(http.StatusBadRequest, "job with hostname '%s' is not running", value.Hostname)
		}
	}

	// TODO check ips
	// TODO check licenses
	// TODO check ingresses

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
	), nil
}

func JobsBrowse(
	actor rpc.Actor,
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

			return true
		},
	), nil
}

func JobsRetrieve(actor rpc.Actor, id string, flags orcapi.JobFlags) (orcapi.Job, *util.HttpError) {
	return ResourceRetrieve[orcapi.Job](actor, jobType, ResourceParseId(id), flags.ResourceFlags)
}

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

		r.Extra = info
	}
}

func jobPersist(tx *db.Transaction, resources []*resource) {
	db.Exec(
		tx,
		`
with data as (
	select
		unnest(cast(:app_names as text[])) app_name,
		unnest(cast(:app_versions as text[])) app_version,
		unnest(cast(:time_allocs as int8[])) time_alloc,
		unnest(cast(:names as text[])) name,
		unnest(cast(:folders as text[])) folder,
		unnest(cast(:states as text[])) state,
		unnest(cast(:started_at as text[])) started_at,
		unnest(cast(:ids as text[])) id,
		unnest(cast(:exported_params as text[])) exported_params,
		unnest(cast(:opened_files as text[])) opened_files
)
insert into app_orchestrator.jobs(application_name, application_version, time_allocation_millis, 
	name, output_folder, current_state, started_at, resource, job_parameters, opened_file) 
values (:app_name, :app_version, :time_alloc, :name, :folder, :current_state, :started_at, 
	:job_id, :params, :opened_file)
	    `,
		db.Params{},
	)
}

func jobTransform(
	r orcapi.Resource,
	product util.Option[accapi.ProductReference],
	extra any,
	flags orcapi.ResourceFlags,
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
		result.Status.ResolvedProduct = support.Product
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
