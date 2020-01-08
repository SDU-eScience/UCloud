create table permissions (
     application_name varchar(255) not null,
     entity varchar(255) not null,
     entity_type varchar(255) not null,
     permission varchar(255) not null,
     primary key (application_name, entity, entity_type)
);

create index permissions_lookup_index on permissions (application_name, entity, entity_type);

alter table applications
    add column is_public boolean default true;
