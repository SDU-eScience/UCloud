drop table application_license_servers;

create table application_license_servers
(
    app_name           varchar(255) not null,
    license_server     varchar(255) not null,
    primary key (app_name, license_server)
);

alter table license_servers
    drop column version;

alter table permissions
    add constraint permissions_server_id_fkey foreign key (server_id) references license_servers (id);

alter table application_license_servers
    add constraint application_license_server_fkey foreign key (license_server) references license_servers (id);
