create sequence hibernate_sequence start with 1 increment by 1;

create table application_license_servers
(
    id             varchar(255) not null,
    name           varchar(255),
    version        varchar(255),
    address        varchar(255),
    owner          varchar(255),
    primary key (id)
);

create table permissions (
    server_id varchar(255) not null,
    entity varchar(2048) not null,
    entity_type varchar(255) not null,
    permission varchar(255),
    primary key (server_id, entity, permission)
);

create index permissions_lookup_index on permissions (server_id, entity, entity_type);
