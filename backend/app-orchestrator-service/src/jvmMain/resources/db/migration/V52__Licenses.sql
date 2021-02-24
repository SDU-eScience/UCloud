drop trigger if exists update_license_state on app_orchestrator.license_updates;
drop function if exists app_orchestrator.update_license_state;
drop table if exists app_orchestrator.license_updates;
drop table if exists app_orchestrator.licenses;

create table app_orchestrator.licenses
(
    id                     text   not null primary key,
    product_id             text   not null,
    product_provider       text   not null,
    product_category       text   not null,
    product_price_per_unit bigint not null,
    created_at             timestamp       default now(),
    owner_username         text   not null,
    owner_project          text,
    current_state          text,
    last_update            timestamp       default now(),
    credits_charged        bigint not null default 0
);

create table app_orchestrator.license_updates
(
    license_id     text,
    timestamp      timestamp default now(),
    state          text      default null,
    status         text      default null,
    foreign key (license_id) references app_orchestrator.licenses (id)
);

create or replace function app_orchestrator.update_license_state() returns trigger as
$$
begin
    update app_orchestrator.licenses
    set current_state   = coalesce(new.state, current_state),
        last_update     = now()
    where id = new.license_id
      and new.timestamp >= last_update;
    return null;
end;
$$ language plpgsql;

create trigger update_license_state
    after insert
    on app_orchestrator.license_updates
    for each row
execute procedure app_orchestrator.update_license_state();
