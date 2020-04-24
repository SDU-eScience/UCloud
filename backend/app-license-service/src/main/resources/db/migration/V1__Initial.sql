create sequence hibernate_sequence start with 1 increment by 1;

create table license_servers
(
    id             varchar(255) not null,
    name           varchar(255) not null,
    version        varchar(255) not null,
    address        varchar(255) not null,
    port           integer not null check (port >= 0 and port <= 65535),
    license        varchar(255),
    primary key (id)
);

create table application_license_servers
(
    app_name           varchar(255) not null,
    app_version        varchar(255) not null,
    license_server     varchar(255) not null,
    primary key (app_name, app_version, license_server)
);

create table permissions (
    server_id varchar(255) not null,
    entity varchar(255) not null,
    entity_type varchar(255) not null,
    permission varchar(255) not null,
    primary key (server_id, entity, entity_type)
);

create index permissions_lookup_index on permissions (server_id, entity, entity_type);
