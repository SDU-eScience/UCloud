insert into provider.resource (type, provider, created_by, project, product, provider_generated_id, created_at)
select 'network_ip', product_provider, owner_username, owner_project,  p.id, i.id, i.created_at
from
    app_orchestrator.network_ips i join
    accounting.product_categories pc on
        i.product_category = pc.category and
        i.product_provider = pc.provider join
    accounting.products p on
        p.category = pc.id and
        p.name = i.product_id;

alter table app_orchestrator.network_ips add column resource bigint references provider.resource(id);

update app_orchestrator.network_ips i
set resource = r.id
from provider.resource r
where r.provider_generated_id = i.id;

insert into provider.resource_update (resource, created_at, status, extra)
select
    r.id, iu.timestamp, iu.status,
    jsonb_build_object(
        'state', iu.state,
        'binding', null,
        'changeIpAddress', iu.change_ip_address,
        'newIpAddress', iu.new_ip_address
    )
from
    app_orchestrator.network_ip_updates iu join
    app_orchestrator.network_ips i on i.id = iu.network_ip_id join
    provider.resource r on i.resource = r.id;

drop table app_orchestrator.network_ip_updates;

alter table app_orchestrator.network_ips drop column product_category;
alter table app_orchestrator.network_ips drop column product_provider;
alter table app_orchestrator.network_ips drop column product_id;
alter table app_orchestrator.network_ips drop column product_price_per_unit;
alter table app_orchestrator.network_ips drop column created_at;
alter table app_orchestrator.network_ips drop column last_update;
alter table app_orchestrator.network_ips drop column credits_charged;
alter table app_orchestrator.network_ips drop column owner_username;
alter table app_orchestrator.network_ips drop column owner_project;
alter table app_orchestrator.network_ips alter column resource set not null;
alter table app_orchestrator.network_ips drop column id;
alter table app_orchestrator.network_ips add constraint network_ip_pkey primary key (resource);
drop function if exists app_orchestrator.update_ingress_state();
alter table app_orchestrator.network_ips alter column current_state set default 'PREPARING';

with
    all_entries as (
        select resource, jsonb_array_elements(acl) elem
        from app_orchestrator.network_ips
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

alter table app_orchestrator.network_ips drop column acl;

alter table app_orchestrator.network_ips add column new_status_bound_to bigint[] not null default array[]::bigint[];

update app_orchestrator.network_ips
set
    new_status_bound_to = case
        when status_bound_to is null then array[]::bigint[]
        else array[status_bound_to]
    end
where true;

alter table app_orchestrator.network_ips drop column status_bound_to;
alter table app_orchestrator.network_ips rename column new_status_bound_to to status_bound_to;

create or replace function app_orchestrator.network_ip_to_json(
    network_in app_orchestrator.network_ips
) returns jsonb language sql as $$
    select jsonb_build_object(
        'specification', jsonb_build_object('firewall', network_in.firewall),
        'status', jsonb_build_object(
            'boundTo', network_in.status_bound_to,
            'state', network_in.current_state,
            'ipAddress', network_in.ip_address
        )
    );
$$;
