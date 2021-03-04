drop table if exists app_kubernetes.license_instances;
drop table if exists app_kubernetes.license_servers;

create table app_kubernetes.license_servers
(
    id      text    not null primary key,
    address text    not null,
    port    integer not null
        constraint license_servers_port_check
            check ((port >= 0) AND (port <= 65535)),
    license text,
    tags    jsonb
);

create table app_kubernetes.license_instances(
    orchestrator_id text not null primary key,
    server_id text not null references app_kubernetes.license_servers
);
