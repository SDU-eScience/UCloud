package orchestrator

import (
	"net/http"
	"strings"
	"time"

	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func jobInitUsageBreakdown() {
	orcapi.JobUsageBreakdownInternal.Handler(jobHandleUsageBreakdown)
	if util.DevelopmentModeEnabled() {
		orcapi.JobUsageBreakdownSeedInternal.Handler(jobHandleUsageBreakdownSeed)
	}
}

func jobHandleUsageBreakdown(
	info rpc.RequestInfo,
	request orcapi.JobUsageBreakdownInternalRequest,
) (orcapi.JobUsageBreakdownResponse, *util.HttpError) {
	_ = info

	startTime := request.Start.Time().UTC()
	endTime := request.End.Time().UTC()
	if endTime.Before(startTime) || len(request.Categories) == 0 || request.BucketSeconds <= 0 || request.BucketCount <= 0 {
		return orcapi.JobUsageBreakdownResponse{
			DailyRows:        []orcapi.JobUsageBreakdownDailyRow{},
			BucketQueueRows:  []orcapi.JobUsageBucketQueueRow{},
			ProductQueueRows: []orcapi.JobUsageProductQueueRow{},
		}, nil
	}

	catProviders := make([]string, 0, len(request.Categories))
	catNames := make([]string, 0, len(request.Categories))
	var exclusiveOwners, weightOwners, weightDays []string
	var exclusiveCategories, weightCategories []int64
	var weightValues []float64
	hasFractionalWeights := false
	seenCategories := map[[2]string]bool{}
	for _, cat := range request.Categories {
		p := strings.TrimSpace(cat.Provider)
		c := strings.TrimSpace(cat.Category)
		key := [2]string{p, c}
		if p == "" || c == "" || seenCategories[key] {
			continue
		}
		seenCategories[key] = true
		catProviders = append(catProviders, p)
		catNames = append(catNames, c)
		categoryIndex := int64(len(catNames))
		exclusiveSet := map[string]bool{}
		for _, owner := range cat.ExclusiveOwners {
			owner = strings.TrimSpace(owner)
			if owner == "" || exclusiveSet[owner] {
				continue
			}
			exclusiveSet[owner] = true
			exclusiveOwners = append(exclusiveOwners, owner)
			exclusiveCategories = append(exclusiveCategories, categoryIndex)
		}
		for _, entry := range cat.DailyWeights {
			owner := strings.TrimSpace(entry.Owner)
			day := strings.TrimSpace(entry.Day)
			if owner == "" || entry.Weight <= 0 || exclusiveSet[owner] {
				continue
			}
			_, dayErr := time.Parse("2006-01-02", day)
			if dayErr != nil {
				continue
			}
			weight := min(entry.Weight, 1.0)
			hasFractionalWeights = hasFractionalWeights || weight < 1
			weightOwners = append(weightOwners, owner)
			weightDays = append(weightDays, day)
			weightValues = append(weightValues, weight)
			weightCategories = append(weightCategories, categoryIndex)
		}
	}

	if len(exclusiveOwners) == 0 && len(weightOwners) == 0 {
		return orcapi.JobUsageBreakdownResponse{
			DailyRows:        []orcapi.JobUsageBreakdownDailyRow{},
			BucketQueueRows:  []orcapi.JobUsageBucketQueueRow{},
			ProductQueueRows: []orcapi.JobUsageProductQueueRow{},
		}, nil
	}

	var dailyRows []orcapi.JobUsageBreakdownDailyRow
	var bucketQueueRows []orcapi.JobUsageBucketQueueRow
	var productQueueRows []orcapi.JobUsageProductQueueRow
	coFundedCount := 0
	var queryErr error

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`set local statement_timeout = '4000'`,
			db.Params{},
		)

		params := db.Params{
			"start_ts":         startTime,
			"end_ts":           endTime,
			"bucket_start":     request.BucketStart.Time().UTC(),
			"bucket_seconds":   request.BucketSeconds,
			"bucket_count":     request.BucketCount,
			"cat_providers":    catProviders,
			"cat_names":        catNames,
			"exclusive_owners": exclusiveOwners,
			"exclusive_scopes": exclusiveCategories,
			"weight_owners":    weightOwners,
			"weight_days":      weightDays,
			"weight_values":    weightValues,
			"weight_scopes":    weightCategories,
		}

		dailyRows = db.Select[orcapi.JobUsageBreakdownDailyRow](
			tx,
			`
				with
					target_categories as (
						select
							pc.id,
							pc.provider,
							t.scope
						from
							unnest(
								cast(:cat_providers as text[]),
								cast(:cat_names as text[])
							) with ordinality as t(provider, category, scope)
							join accounting.product_categories pc on
								pc.provider = t.provider
								and pc.category = t.category
						where
							pc.product_type = 'COMPUTE'
					),
					exclusive_owners as (
						select owner, scope
						from unnest(cast(:exclusive_owners as text[]), cast(:exclusive_scopes as bigint[])) as t(owner, scope)
					),
					daily_weights as (
						select
							owner,
							cast(day as date) as usage_day,
							weight,
							scope
						from unnest(
							cast(:weight_owners as text[]),
							cast(:weight_days as text[]),
							cast(:weight_values as double precision[]),
							cast(:weight_scopes as bigint[])
						) as t(owner, day, weight, scope)
					),
					matched_resources as (
						select
							r.id,
							r.created_at,
							r.product,
							cast(1.0 as double precision) as weight,
							eo.scope
						from
							provider.resource r
							join exclusive_owners eo on r.project = eo.owner
						where
							r.type = 'job'
							and r.project is not null
							and r.created_at >= cast(:start_ts as timestamptz)
							and r.created_at <= cast(:end_ts as timestamptz)
						union all
						select
							r.id,
							r.created_at,
							r.product,
							cast(1.0 as double precision) as weight,
							eo.scope
						from
							provider.resource r
							join exclusive_owners eo on r.created_by = eo.owner
						where
							r.type = 'job'
							and r.project is null
							and r.created_at >= cast(:start_ts as timestamptz)
							and r.created_at <= cast(:end_ts as timestamptz)
						union all
						select
							r.id,
							r.created_at,
							r.product,
							dw.weight,
							dw.scope
						from
							provider.resource r
							join daily_weights dw on
								r.project = dw.owner
								and cast((r.created_at at time zone 'utc') as date) = dw.usage_day
						where
							r.type = 'job'
							and r.project is not null
							and r.created_at >= cast(:start_ts as timestamptz)
							and r.created_at <= cast(:end_ts as timestamptz)
						union all
						select
							r.id,
							r.created_at,
							r.product,
							dw.weight,
							dw.scope
						from
							provider.resource r
							join daily_weights dw on
								r.created_by = dw.owner
								and cast((r.created_at at time zone 'utc') as date) = dw.usage_day
						where
							r.type = 'job'
							and r.project is null
							and r.created_at >= cast(:start_ts as timestamptz)
							and r.created_at <= cast(:end_ts as timestamptz)
					),
					scoped_jobs as (
						select
							cast((mr.created_at at time zone 'utc') as date) as usage_day,
							j.application_name as application,
							p.name as product,
							tc.provider as provider,
							case
								when coalesce(
									state_at_end.state,
									case
										when j.last_update at time zone 'utc' <= cast(:end_ts as timestamptz) then j.current_state
										else 'RUNNING'
									end
								) = 'SUCCESS' then 'completed'
								when coalesce(
									state_at_end.state,
									case
										when j.last_update at time zone 'utc' <= cast(:end_ts as timestamptz) then j.current_state
										else 'RUNNING'
									end
								) = 'FAILURE' then 'failed'
								when coalesce(
									state_at_end.state,
									case
										when j.last_update at time zone 'utc' <= cast(:end_ts as timestamptz) then j.current_state
										else 'RUNNING'
									end
								) = 'EXPIRED' then 'expired'
								else 'active'
							end as outcome,
							mr.weight as weight,
							coalesce(
								runtime.running_seconds,
								case when j.started_at is not null then
									greatest(0, extract(epoch from (
										least(cast(:end_ts as timestamptz), now(),
											case when j.current_state = 'RUNNING' then now()
											else j.last_update at time zone 'utc' end
										) - greatest(j.started_at at time zone 'utc', cast(:start_ts as timestamptz))
									)))
								else 0 end
							) / 3600.0
								* j.replicas
								* (
									case
										when coalesce(p.gpu, 0) > 0 then cast(p.gpu as double precision)
										else cast(greatest(coalesce(p.cpu, 1), 1) as double precision)
									end
								)
								* (
									case
										when coalesce(p.fraction_numerator, 0) > 0 and coalesce(p.fraction_denominator, 0) > 0
										then cast(p.fraction_numerator as double precision) / cast(p.fraction_denominator as double precision)
										else 1.0
									end
								) as actual_hours,
							coalesce(j.time_allocation_millis, 0) / 3600000.0
								* j.replicas
								* (
									case
										when coalesce(p.gpu, 0) > 0 then cast(p.gpu as double precision)
										else cast(greatest(coalesce(p.cpu, 1), 1) as double precision)
									end
								)
								* (
									case
										when coalesce(p.fraction_numerator, 0) > 0 and coalesce(p.fraction_denominator, 0) > 0
										then cast(p.fraction_numerator as double precision) / cast(p.fraction_denominator as double precision)
										else 1.0
									end
								) as requested_hours
						from
							matched_resources mr
							join app_orchestrator.jobs j on j.resource = mr.id
							join accounting.products p on p.id = mr.product
							join target_categories tc on tc.id = p.category and tc.scope = mr.scope
							left join lateral (
								select
									u.extra->>'state' as state
								from
									provider.resource_update u
								where
									u.resource = mr.id
									and u.extra->>'state' is not null
									and u.created_at <= cast(:end_ts as timestamptz)
								order by
									u.created_at desc,
									u.id desc
								limit 1
							) state_at_end on true
							left join lateral (
								select sum(greatest(0, extract(epoch from (
									least(
										coalesce(next_at, case when j.current_state = 'RUNNING' then now()
											else j.last_update at time zone 'utc' end),
										cast(:end_ts as timestamptz),
										now()
									)
									- greatest(created_at, cast(:start_ts as timestamptz))
								)))) as running_seconds
								from (
									select
										u.created_at,
										u.extra->>'state' as state,
										lead(u.created_at) over (order by u.created_at, u.id) as next_at
									from
										provider.resource_update u
									where
										u.resource = mr.id
										and u.extra->>'state' is not null
								) states
								where state = 'RUNNING'
							) runtime on true
					)
				select
					to_char(usage_day, 'YYYY-MM-DD') as day,
					application,
					product,
					provider,
					outcome,
					sum(weight) as job_count,
					sum(actual_hours * weight) as estimated_hours,
					sum(requested_hours * weight) as requested_hours
				from
					scoped_jobs
				group by
					usage_day,
					application,
					product,
					provider,
					outcome
				order by
					usage_day,
					application,
					product,
					outcome
			`,
			params,
		)

		bucketQueueRows = db.Select[orcapi.JobUsageBucketQueueRow](
			tx,
			`
				with
					target_categories as (
						select
							pc.id,
							t.scope
						from
							unnest(
								cast(:cat_providers as text[]),
								cast(:cat_names as text[])
							) with ordinality as t(provider, category, scope)
							join accounting.product_categories pc on
								pc.provider = t.provider
								and pc.category = t.category
						where
							pc.product_type = 'COMPUTE'
					),
					exclusive_owners as (
						select owner, scope
						from unnest(cast(:exclusive_owners as text[]), cast(:exclusive_scopes as bigint[])) as t(owner, scope)
					),
					daily_weights as (
						select
							owner,
							cast(day as date) as usage_day,
							scope
						from unnest(
							cast(:weight_owners as text[]),
							cast(:weight_days as text[]),
							cast(:weight_scopes as bigint[])
						) as t(owner, day, scope)
					),
					matched_resources as (
						select
							r.id,
							r.created_at,
							r.product,
							eo.scope
						from
							provider.resource r
							join exclusive_owners eo on r.project = eo.owner
						where
							r.type = 'job'
							and r.project is not null
							and r.created_at >= cast(:start_ts as timestamptz)
							and r.created_at <= cast(:end_ts as timestamptz)
						union all
						select
							r.id,
							r.created_at,
							r.product,
							eo.scope
						from
							provider.resource r
							join exclusive_owners eo on r.created_by = eo.owner
						where
							r.type = 'job'
							and r.project is null
							and r.created_at >= cast(:start_ts as timestamptz)
							and r.created_at <= cast(:end_ts as timestamptz)
						union all
						select
							r.id,
							r.created_at,
							r.product,
							dw.scope
						from
							provider.resource r
							join daily_weights dw on
								r.project = dw.owner
								and cast((r.created_at at time zone 'utc') as date) = dw.usage_day
						where
							r.type = 'job'
							and r.project is not null
							and r.created_at >= cast(:start_ts as timestamptz)
							and r.created_at <= cast(:end_ts as timestamptz)
						union all
						select
							r.id,
							r.created_at,
							r.product,
							dw.scope
						from
							provider.resource r
							join daily_weights dw on
								r.created_by = dw.owner
								and cast((r.created_at at time zone 'utc') as date) = dw.usage_day
						where
							r.type = 'job'
							and r.project is null
							and r.created_at >= cast(:start_ts as timestamptz)
							and r.created_at <= cast(:end_ts as timestamptz)
					),
					queue_samples as (
						select
							least(cast(:bucket_count as integer) - 1, greatest(0, cast(floor(
								extract(epoch from (mr.created_at - cast(:bucket_start as timestamptz))) / cast(:bucket_seconds as bigint)
							) as integer))) as bucket_index,
							extract(epoch from (coalesce(first_run.started_at, j.started_at at time zone 'utc') - mr.created_at)) as queue_seconds
						from
							matched_resources mr
							join app_orchestrator.jobs j on j.resource = mr.id
							join accounting.products p on p.id = mr.product
							join target_categories tc on tc.id = p.category and tc.scope = mr.scope
							left join lateral (
								select min(u.created_at) as started_at
								from provider.resource_update u
								where u.resource = mr.id and u.extra->>'state' = 'RUNNING'
							) first_run on true
						where
							coalesce(first_run.started_at, j.started_at at time zone 'utc') >= mr.created_at
							and coalesce(first_run.started_at, j.started_at at time zone 'utc') <= cast(:end_ts as timestamptz)
					)
				select
					bucket_index,
					count(*) as sample_count,
					avg(queue_seconds) as avg_queue_seconds,
					percentile_cont(0.50) within group (order by queue_seconds) as p50_queue_seconds,
					percentile_cont(0.90) within group (order by queue_seconds) as p90_queue_seconds
				from
					queue_samples
				group by
					bucket_index
				order by
					bucket_index
			`,
			params,
		)

		productQueueRows = db.Select[orcapi.JobUsageProductQueueRow](
			tx,
			`
				with
					target_categories as (
						select
							pc.id,
							pc.provider,
							t.scope
						from
							unnest(
								cast(:cat_providers as text[]),
								cast(:cat_names as text[])
							) with ordinality as t(provider, category, scope)
							join accounting.product_categories pc on
								pc.provider = t.provider
								and pc.category = t.category
						where
							pc.product_type = 'COMPUTE'
					),
					exclusive_owners as (
						select owner, scope
						from unnest(cast(:exclusive_owners as text[]), cast(:exclusive_scopes as bigint[])) as t(owner, scope)
					),
					daily_weights as (
						select
							owner,
							cast(day as date) as usage_day,
							scope
						from unnest(
							cast(:weight_owners as text[]),
							cast(:weight_days as text[]),
							cast(:weight_scopes as bigint[])
						) as t(owner, day, scope)
					),
					matched_resources as (
						select
							r.id,
							r.created_at,
							r.product,
							eo.scope
						from
							provider.resource r
							join exclusive_owners eo on r.project = eo.owner
						where
							r.type = 'job'
							and r.project is not null
							and r.created_at >= cast(:start_ts as timestamptz)
							and r.created_at <= cast(:end_ts as timestamptz)
						union all
						select
							r.id,
							r.created_at,
							r.product,
							eo.scope
						from
							provider.resource r
							join exclusive_owners eo on r.created_by = eo.owner
						where
							r.type = 'job'
							and r.project is null
							and r.created_at >= cast(:start_ts as timestamptz)
							and r.created_at <= cast(:end_ts as timestamptz)
						union all
						select
							r.id,
							r.created_at,
							r.product,
							dw.scope
						from
							provider.resource r
							join daily_weights dw on
								r.project = dw.owner
								and cast((r.created_at at time zone 'utc') as date) = dw.usage_day
						where
							r.type = 'job'
							and r.project is not null
							and r.created_at >= cast(:start_ts as timestamptz)
							and r.created_at <= cast(:end_ts as timestamptz)
						union all
						select
							r.id,
							r.created_at,
							r.product,
							dw.scope
						from
							provider.resource r
							join daily_weights dw on
								r.created_by = dw.owner
								and cast((r.created_at at time zone 'utc') as date) = dw.usage_day
						where
							r.type = 'job'
							and r.project is null
							and r.created_at >= cast(:start_ts as timestamptz)
							and r.created_at <= cast(:end_ts as timestamptz)
					),
					product_jobs as (
						select
							p.name as product,
							tc.provider as provider,
							coalesce(p.cpu, 0) as cpu,
							coalesce(p.gpu, 0) as gpu,
							coalesce(p.memory_in_gigs, 0) as memory_in_gigs,
							case
								when coalesce(first_run.started_at, j.started_at at time zone 'utc')
									between mr.created_at and cast(:end_ts as timestamptz)
								then extract(epoch from (coalesce(first_run.started_at, j.started_at at time zone 'utc') - mr.created_at))
								else null
							end as queue_seconds
						from
							matched_resources mr
							join app_orchestrator.jobs j on j.resource = mr.id
							join accounting.products p on p.id = mr.product
							join target_categories tc on tc.id = p.category and tc.scope = mr.scope
							left join lateral (
								select min(u.created_at) as started_at
								from provider.resource_update u
								where u.resource = mr.id and u.extra->>'state' = 'RUNNING'
							) first_run on true
					)
				select
					product,
					provider,
					max(cpu) as cpu,
					max(gpu) as gpu,
					max(memory_in_gigs) as memory_in_gigs,
					count(queue_seconds) as sample_count,
					coalesce(avg(queue_seconds), 0) as avg_queue_seconds,
					coalesce(percentile_cont(0.50) within group (order by queue_seconds), 0) as p50_queue_seconds,
					coalesce(percentile_cont(0.90) within group (order by queue_seconds), 0) as p90_queue_seconds,
					coalesce(max(queue_seconds), 0) as max_queue_seconds
				from
					product_jobs
				group by
					product,
					provider
				order by
					product,
					provider
			`,
			params,
		)

		if hasFractionalWeights {
			countRow, ok := db.Get[struct {
				Count int
			}](
				tx,
				`
					with
						target_categories as (
							select
								pc.id,
								t.scope
							from
								unnest(
									cast(:cat_providers as text[]),
									cast(:cat_names as text[])
								) with ordinality as t(provider, category, scope)
								join accounting.product_categories pc on
									pc.provider = t.provider
									and pc.category = t.category
							where
								pc.product_type = 'COMPUTE'
						),
						daily_weights as (
							select
								owner,
								cast(day as date) as usage_day,
								scope
							from unnest(
								cast(:weight_owners as text[]),
								cast(:weight_days as text[]),
								cast(:weight_values as double precision[]),
								cast(:weight_scopes as bigint[])
							) as t(owner, day, weight, scope)
							where
								weight < 1.0
						),
						weighted_owners as (
							select
								r.project as owner,
								r.product,
								dw.scope
							from
								provider.resource r
								join daily_weights dw on
									r.project = dw.owner
									and cast((r.created_at at time zone 'utc') as date) = dw.usage_day
							where
								r.type = 'job'
								and r.project is not null
								and r.created_at >= cast(:start_ts as timestamptz)
								and r.created_at <= cast(:end_ts as timestamptz)
							union all
							select
								r.created_by as owner,
								r.product,
								dw.scope
							from
								provider.resource r
								join daily_weights dw on
									r.created_by = dw.owner
									and cast((r.created_at at time zone 'utc') as date) = dw.usage_day
							where
								r.type = 'job'
								and r.project is null
								and r.created_at >= cast(:start_ts as timestamptz)
								and r.created_at <= cast(:end_ts as timestamptz)
						)
					select
						count(distinct wo.owner) as count
					from
						weighted_owners wo
						join accounting.products p on p.id = wo.product
						join target_categories tc on tc.id = p.category and tc.scope = wo.scope
				`,
				db.Params{
					"start_ts":      startTime,
					"end_ts":        endTime,
					"cat_providers": catProviders,
					"cat_names":     catNames,
					"weight_owners": weightOwners,
					"weight_days":   weightDays,
					"weight_values": weightValues,
					"weight_scopes": weightCategories,
				},
			)
			if ok {
				coFundedCount = countRow.Count
			}
		}

		queryErr = tx.ConsumeError()
	})

	if queryErr != nil {
		log.Warn("Could not query job usage breakdown: %s", queryErr.Error())
		return orcapi.JobUsageBreakdownResponse{}, util.HttpErr(http.StatusInternalServerError, "could not query job usage breakdown")
	}

	return orcapi.JobUsageBreakdownResponse{
		DailyRows:              util.NonNilSlice(dailyRows),
		BucketQueueRows:        util.NonNilSlice(bucketQueueRows),
		ProductQueueRows:       util.NonNilSlice(productQueueRows),
		CoFundedWorkspaceCount: coFundedCount,
	}, nil
}
