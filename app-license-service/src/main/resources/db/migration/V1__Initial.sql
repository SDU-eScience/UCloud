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
    application_name varchar(255) not null,
    application_version varchar(255) not null,
    username varchar(2048) not null,
    permission varchar(255),
    primary key (application_name, application_version, username, permission)
);

create index permissions_lookup_index on permissions (application_name, application_version, username);
