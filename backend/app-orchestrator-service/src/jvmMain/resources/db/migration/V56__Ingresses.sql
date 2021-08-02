drop table if exists app_orchestrator.public_links;

insert into provider.resource (type, provider, created_by, project, product, provider_generated_id, created_at)
select 'ingress', product_provider, owner_username, owner_project,  p.id, i.id, i.created_at
from
    app_orchestrator.ingresses i join
    accounting.product_categories pc on
        i.product_category = pc.category and
        i.product_provider = pc.provider join
    accounting.products p on
        p.category = pc.id and
        p.name = i.product_id;

alter table app_orchestrator.ingresses add column resource bigint references provider.resource(id);

update app_orchestrator.ingresses i
set resource = r.id
from provider.resource r
where r.provider_generated_id = i.id;

insert into provider.resource_update (resource, created_at, status, extra)
select
    r.id, iu.timestamp, iu.status,
    jsonb_build_object(
        'state', iu.state,
        'didBind', iu.change_binding,
        'newBinding', iu.bound_to
    )
from
    app_orchestrator.ingress_updates iu join
    app_orchestrator.ingresses i on i.id = iu.ingress_id join
    provider.resource r on i.resource = r.id;

drop table app_orchestrator.ingress_updates;

alter table app_orchestrator.ingresses drop column product_category;
alter table app_orchestrator.ingresses drop column product_provider;
alter table app_orchestrator.ingresses drop column product_id;
alter table app_orchestrator.ingresses drop column product_price_per_unit;
alter table app_orchestrator.ingresses drop column created_at;
alter table app_orchestrator.ingresses drop column last_update;
alter table app_orchestrator.ingresses drop column credits_charged;
alter table app_orchestrator.ingresses drop column owner_username;
alter table app_orchestrator.ingresses drop column owner_project;
alter table app_orchestrator.ingresses alter column resource set not null;
alter table app_orchestrator.ingresses drop column id;
alter table app_orchestrator.ingresses add constraint ingress_pkey primary key (resource);
drop function if exists app_orchestrator.update_ingress_state();
alter table app_orchestrator.ingresses alter column status_bound_to set default null;
alter table app_orchestrator.ingresses alter column current_state set default 'PREPARING';

create or replace function app_orchestrator.ingress_to_json(
    ingress_in app_orchestrator.ingresses
) returns jsonb language sql as $$
    select jsonb_build_object(
        'specification', jsonb_build_object(
            'domain', ingress_in.domain
        ),
        'status', jsonb_build_object(
            'boundTo', ingress_in.status_bound_to,
            'state', ingress_in.current_state
        )
    );
$$;
