drop table if exists app_orchestrator.job_information;

with invalid_jobs as (
    select j.id as invalid_id
    from
        app_orchestrator.jobs j left join
        auth.principals u on j.launched_by = u.id
    where u.id is null
)
update app_orchestrator.jobs j
set launched_by = 'ghost'
from invalid_jobs
where invalid_id = j.id;

update app_orchestrator.jobs
set
    product_category = 'u1-standard',
    product_id = 'u1-standard-1'
where
    product_category = 'standard' and
    product_provider = 'ucloud';

create temporary table invalid_jobs_to_delete as
select job.id as invalid_id
from
    app_orchestrator.jobs job left join
    project.projects p on job.project = p.id
where
    p.id is null and
    job.project is not null;

delete from app_orchestrator.job_updates
using invalid_jobs_to_delete
where job_id = invalid_id;

delete from app_orchestrator.job_input_parameters
using invalid_jobs_to_delete
where job_id = invalid_id;

delete from app_orchestrator.job_resources
using invalid_jobs_to_delete
where job_id = invalid_id;

delete from app_orchestrator.jobs
using invalid_jobs_to_delete
where id = invalid_id;

insert into provider.resource (type, provider, created_by, project, product, provider_generated_id, created_at)
select 'job', product_provider, launched_by, project,  p.id, j.id, j.created_at
from
    app_orchestrator.jobs j join
    accounting.product_categories pc on
        j.product_category = pc.category and
        j.product_provider = pc.provider join
    accounting.products p on
        p.category = pc.id and
        p.name = j.product_id;

alter table app_orchestrator.jobs add column resource bigint references provider.resource(id);

update app_orchestrator.jobs i
set resource = r.id
from provider.resource r
where r.provider_generated_id = i.id;

insert into provider.resource_update (resource, created_at, status, extra)
select
    r.id, iu.ts, iu.status,
    jsonb_build_object('state', iu.state)
from
    app_orchestrator.job_updates iu join
    app_orchestrator.jobs i on i.id = iu.job_id join
    provider.resource r on i.resource = r.id;

drop table app_orchestrator.job_updates;

drop function if exists app_orchestrator.update_state();

-- Fix references: job_input_parameters
alter table app_orchestrator.job_input_parameters add column new_id bigint;

update app_orchestrator.job_input_parameters i
set new_id = r.id
from provider.resource r
where r.provider_generated_id = i.job_id;

alter table app_orchestrator.job_input_parameters drop column job_id;
alter table app_orchestrator.job_input_parameters alter column new_id set not null;
alter table app_orchestrator.job_input_parameters rename column new_id to job_id;
-- /Fix references

-- Fix references: job_resources
alter table app_orchestrator.job_resources add column new_id bigint;

update app_orchestrator.job_resources i
set new_id = r.id
from provider.resource r
where r.provider_generated_id = i.job_id;

alter table app_orchestrator.job_resources drop column job_id;
alter table app_orchestrator.job_resources alter column new_id set not null;
alter table app_orchestrator.job_resources rename column new_id to job_id;
-- /Fix references

-- Fix references: ingresses
alter table app_orchestrator.ingresses add column new_status_bound_to bigint;

update app_orchestrator.ingresses i
set new_status_bound_to = r.id
from provider.resource r
where r.provider_generated_id = i.status_bound_to;

alter table app_orchestrator.ingresses drop column status_bound_to;
alter table app_orchestrator.ingresses rename column new_status_bound_to to status_bound_to;
-- /Fix references

-- Fix references: network_ips
alter table app_orchestrator.network_ips add column new_status_bound_to bigint;

update app_orchestrator.network_ips i
set new_status_bound_to = r.id
from provider.resource r
where r.provider_generated_id = i.status_bound_to;

alter table app_orchestrator.network_ips drop column status_bound_to;
alter table app_orchestrator.network_ips rename column new_status_bound_to to status_bound_to;
-- /Fix references

alter table app_orchestrator.jobs drop column id;
alter table app_orchestrator.jobs alter column resource set not null;
alter table app_orchestrator.jobs add constraint jobs_pkey primary key (resource);
alter table app_orchestrator.job_input_parameters add foreign key (job_id) references app_orchestrator.jobs(resource);
alter table app_orchestrator.job_resources add foreign key (job_id) references app_orchestrator.jobs(resource);
alter table app_orchestrator.jobs drop column refresh_token;
alter table app_orchestrator.jobs drop column price_per_unit;
alter table app_orchestrator.jobs drop column credits_charged;
alter table app_orchestrator.jobs drop column product_provider;
alter table app_orchestrator.jobs drop column product_category;
alter table app_orchestrator.jobs drop column product_id;
alter table app_orchestrator.jobs drop column initial_started_at;
alter table app_orchestrator.jobs drop column created_at;
alter table app_orchestrator.jobs drop column project;
alter table app_orchestrator.jobs drop column launched_by;

create type app_orchestrator.job_with_dependencies as (
    resource bigint,
    job app_orchestrator.jobs,
    resources app_orchestrator.job_resources[],
    parameters app_orchestrator.job_input_parameters[],
    application app_store.applications,
    tool app_store.tools
);

create or replace function app_store.tool_to_json(
    tool app_store.tools
) returns jsonb language sql as $$
    select jsonb_build_object(
        'owner', tool.owner,
        'createdAt', floor(extract(epoch from tool.created_at) * 1000),
        'modifiedAt', floor(extract(epoch from tool.modified_at) * 1000),
        'description', tool.tool || jsonb_build_object(
            'info', jsonb_build_object(
                'name', tool.name,
                'version', tool.version
            )
        )
    );
$$;

create or replace function app_store.application_to_json(
    app app_store.applications,
    tool app_store.tools = null
) returns jsonb language sql as $$
    select jsonb_build_object(
        'metadata', jsonb_build_object(
            'name', app.name,
            'version', app.version,
            'authors', app.authors,
            'title', app.title,
            'description', app.description,
            'website', app.website,
            'public', app.is_public
        ),
        'invocation', app.application || jsonb_build_object(
            'tool', jsonb_build_object(
                'name', app.tool_name,
                'version', app.version,
                'tool', app_store.tool_to_json(tool)
            )
        )
    )
$$;

create or replace function app_orchestrator.job_to_json(
    job_with_deps app_orchestrator.job_with_dependencies
) returns jsonb language sql as $$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'application', jsonb_build_object(
                'name', (job_with_deps.job).application_name,
                'version', (job_with_deps.job).application_version
            ),
            'name', (job_with_deps.job).name,
            'replicas', (job_with_deps.job).replicas,
            'timeAllocation', jsonb_build_object(
                'hours', ((job_with_deps.job).time_allocation_millis) / (1000 * 60 * 60),
                'minutes', (((job_with_deps.job).time_allocation_millis) % (1000 * 60 * 60)) / (1000 * 60),
                'seconds', ((((job_with_deps.job).time_allocation_millis) % (1000 * 60 * 60)) % (1000 * 60) / 1000)
            ),
            'parameters', (
                select jsonb_object_agg(p.name, p.value)
                from unnest(job_with_deps.parameters) p
            ),
            'resources', (
                select jsonb_agg(r.resource)
                from unnest(job_with_deps.resources) r
            )
        ),
        'output', jsonb_build_object(
            'outputFolder', (job_with_deps.job).output_folder
        ),
        'status', jsonb_build_object(
            'state', (job_with_deps.job).current_state,
            'startedAt', floor(extract(epoch from (job_with_deps.job).started_at) * 1000),
            'expiresAt', floor(extract(epoch from (job_with_deps.job).started_at) * 1000) +
                (job_with_deps.job).time_allocation_millis,
            'resolvedApplication', app_store.application_to_json(job_with_deps.application, job_with_deps.tool)
        )
    )
$$;
