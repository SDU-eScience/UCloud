package orchestrator

import (
	"database/sql"
	"encoding/json"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
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

	orcapi.JobsBrowse.Handler(func(info rpc.RequestInfo, request orcapi.JobsBrowseRequest) (fndapi.PageV2[orcapi.Job], *util.HttpError) {
		return ResourceBrowse(
			info.Actor,
			jobType,
			request.Next,
			request.ItemsPerPage,
			request.JobFlags.ResourceFlags,
			func(item orcapi.Job) bool {
				// TODO filters
				return true
			},
		), nil
	})
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

		r.Extra = info
	}
}

func jobPersist(tx *db.Transaction, resources []*resource) {

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
		Updates:  info.Updates,
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
		Output: orcapi.JobOutput{},
	}

	if flags.IncludeProduct || flags.IncludeSupport {
		support, _ := SupportByProduct[orcapi.JobSupport](jobType, product.Value)
		result.Status.ResolvedProduct = support.Product
		result.Status.ResolvedSupport = support
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
