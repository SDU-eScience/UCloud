create table permissions (
     application_name varchar(255) not null,
     application_version varchar(255),
     entity varchar(255) not null,
     entity_type varchar(255) not null,
     permission varchar(255) not null,
     primary key (application_name, application_version, entity, entity_type),
     constraint permissions_application_fkey foreign key (application_name, application_version) references applications (name, version)
);

create index permissions_lookup_index on permissions (application_name, application_version, entity, entity_type);
