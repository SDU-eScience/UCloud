drop trigger if exists update_ingress_state on app_orchestrator.ingress_updates;
drop function if exists app_orchestrator.update_ingress_state;
drop table if exists app_orchestrator.ingress_updates;
drop table if exists app_orchestrator.ingresses;

create table app_orchestrator.ingresses
(
    id                     text   not null primary key,
    domain                 text   not null,
    product_id             text   not null,
    product_provider       text   not null,
    product_category       text   not null,
    product_price_per_unit bigint not null,
    created_at             timestamp       default now(),
    owner_username         text   not null,
    owner_project          text,
    status_bound_to        text            default null,
    current_state          text,
    last_update            timestamp       default now(),
    credits_charged        bigint not null default 0,
    foreign key (status_bound_to) references app_orchestrator.jobs (id)
);

create table app_orchestrator.ingress_updates
(
    ingress_id     text,
    timestamp      timestamp default now(),
    state          text      default null,
    status         text      default null,
    change_binding bool      default false,
    bound_to       text      default null,
    foreign key (ingress_id) references app_orchestrator.ingresses (id)
);

create or replace function app_orchestrator.update_ingress_state() returns trigger as
$$
begin
    update app_orchestrator.ingresses
    set current_state   = coalesce(new.state, current_state),
        status_bound_to =
            case new.change_binding
                when true then new.bound_to
                else status_bound_to
                end,
        last_update     = now()
    where id = new.ingress_id
      and new.timestamp >= last_update;
    return null;
end;
$$ language plpgsql;

create trigger update_ingress_state
    after insert
    on app_orchestrator.ingress_updates
    for each row
execute procedure app_orchestrator.update_ingress_state();

alter table app_orchestrator.missed_payments
    drop column if exists type;

alter table app_orchestrator.missed_payments
    add column type text;

update app_orchestrator.missed_payments
set type = 'job'
where true;
alter table app_orchestrator.missed_payments
    alter column type set not null;
