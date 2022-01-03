alter table app_orchestrator.ingresses add column new_status_bound_to bigint[] not null default array[]::bigint[];

update app_orchestrator.ingresses
set
    new_status_bound_to = case
        when status_bound_to is null then array[]::bigint[]
        else array[status_bound_to]
    end
where true;

alter table app_orchestrator.ingresses drop column status_bound_to;
alter table app_orchestrator.ingresses rename column new_status_bound_to to status_bound_to;

update provider.resource_update
set
    extra = extra #- '{"didBind"}' #- '{"newBinding"}' || '{"binding": null}'
from provider.resource r
where
      r.id = resource and
      r.type = 'ingress';
