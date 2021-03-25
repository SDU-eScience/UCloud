drop trigger if exists update_network_ip_state on app_orchestrator.network_ip_updates;
drop function if exists app_orchestrator.update_network_ip_state;
drop table if exists app_orchestrator.network_ip_updates;
drop table if exists app_orchestrator.network_ips;

create table app_orchestrator.network_ips
(
    id                     text   not null primary key,
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
    acl                    jsonb           default '[]'::jsonb,
    firewall               jsonb           default '{
      "openPorts": []
    }'::jsonb,
    ip_address             text            default null,
    foreign key (status_bound_to) references app_orchestrator.jobs (id)
);

create table app_orchestrator.network_ip_updates
(
    network_ip_id     text,
    timestamp         timestamp default now(),
    state             text      default null,
    status            text      default null,
    change_binding    bool      default false,
    bound_to          text      default null,
    change_ip_address bool      default null,
    new_ip_address    text      default null,
    foreign key (network_ip_id) references app_orchestrator.network_ips (id)
);

create or replace function app_orchestrator.update_network_ip_state() returns trigger as
$$
begin
    update app_orchestrator.network_ips
    set current_state   = coalesce(new.state, current_state),
        status_bound_to =
            case new.change_binding
                when true then new.bound_to
                else status_bound_to
                end,
        ip_address      =
            case new.change_ip_address
                when true then new.new_ip_address
                else ip_address
                end,
        last_update     = now()
    where id = new.network_ip_id
      and new.timestamp >= last_update;
    return null;
end;
$$ language plpgsql;

create trigger update_network_ip_state
    after insert
    on app_orchestrator.network_ip_updates
    for each row
execute procedure app_orchestrator.update_network_ip_state();
