insert into provider.resource (type, provider, created_by, project, product, provider_generated_id, created_at)
select 'license', product_provider, owner_username, owner_project,  p.id, i.id, i.created_at
from
    app_orchestrator.licenses i join
    accounting.product_categories pc on
        i.product_category = pc.category and
        i.product_provider = pc.provider join
    accounting.products p on
        p.category = pc.id and
        p.name = i.product_id;

alter table app_orchestrator.licenses add column resource bigint references provider.resource(id);

update app_orchestrator.licenses i
set resource = r.id
from provider.resource r
where r.provider_generated_id = i.id;

insert into provider.resource_update (resource, created_at, status, extra)
select
    r.id, iu.timestamp, iu.status,
    jsonb_build_object(
        'state', iu.state,
        'binding', null
    )
from
    app_orchestrator.license_updates iu join
    app_orchestrator.licenses i on i.id = iu.license_id join
    provider.resource r on i.resource = r.id;

drop table app_orchestrator.license_updates;

alter table app_orchestrator.licenses drop column product_category;
alter table app_orchestrator.licenses drop column product_provider;
alter table app_orchestrator.licenses drop column product_id;
alter table app_orchestrator.licenses drop column product_price_per_unit;
alter table app_orchestrator.licenses drop column created_at;
alter table app_orchestrator.licenses drop column last_update;
alter table app_orchestrator.licenses drop column credits_charged;
alter table app_orchestrator.licenses drop column owner_username;
alter table app_orchestrator.licenses drop column owner_project;
alter table app_orchestrator.licenses alter column resource set not null;
alter table app_orchestrator.licenses drop column id;
alter table app_orchestrator.licenses add constraint license_pkey primary key (resource);
drop function if exists app_orchestrator.update_ingress_state();
alter table app_orchestrator.licenses alter column current_state set default 'PREPARING';

with
    all_entries as (
        select resource, jsonb_array_elements(acl) elem
        from app_orchestrator.licenses
    ),
    relevant_entries as (
        select resource, elem -> 'entity' ->> 'group' as group_id, jsonb_array_elements_text(elem -> 'permissions') as permission
        from all_entries
        where (elem -> 'entity' -> 'type') = '"project_group"'
    ),
    only_edit as (
        select resource, group_id
        from relevant_entries
        where permission = 'USE'
    )
insert into provider.resource_acl_entry (group_id, username, permission, resource_id)
select group_id, null, 'EDIT', resource
from only_edit;

alter table app_orchestrator.licenses drop column acl;
alter table app_orchestrator.licenses add column status_bound_to bigint[] not null default array[]::bigint[];

create or replace function app_orchestrator.license_to_json(
    license_in app_orchestrator.licenses
) returns jsonb language sql as $$
    select jsonb_build_object(
        'status', jsonb_build_object(
            'boundTo', license_in.status_bound_to,
            'state', license_in.current_state
        )
    );
$$;
