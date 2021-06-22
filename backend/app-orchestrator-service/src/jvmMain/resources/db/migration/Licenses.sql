insert into provider.resource (type, provider, created_by, project, product, provider_generated_id, created_at)
select 'license', product_provider, owner_username, owner_project, p.id, l.id, l.created_at
from
    app_orchestrator.licenses l join
    accounting.product_categories pc on
        l.product_category = pc.category and
        l.product_provider = pc.provider join
            accounting.products p on
                p.category = pc.id and p.name = l.product_id;

alter table app_orchestrator.licenses add column resource bigint references provider.resource(id);

update app_orchestrator.licenses l
set resource = r.id
from provider.resource r
where r.provider_generated_id = l.id;

/* Is this needed? */
insert into provider.resource_update (resource, created_at, status, extra)
select
    r.id, lu.timestamp, lu.status,
    json_build_object(
        'state', lu.state,
        'didBind', lu.change_binding,
        'newBinding', lu.bound_to
        )
from
    app_orchestrator.license_updates lu join
    app_orchestrator.licenses l on l.id = lu.license_id join
    provider.resource r on l.resource = r.id;

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
drop function if exists app_orchestrator.update_license_state();
alter table app_orchestrator.licenses alter column current_state set default 'PREPARING';

select * from app_orchestrator.licenses where

create or replace function app_orchestrator.license_to_json(
    license_in app_orchestrator.licenses
) returns jsonb language sql as $$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'product', license_in
        ),

    )
$$;