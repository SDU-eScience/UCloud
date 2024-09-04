create table app_store.curators(
    id text primary key,
    public_read bool not null default false,
    can_create_categories bool not null default false,
    can_manage_catalog bool not null default false,
    managed_by_project_id text not null
);

insert into app_store.curators(id, managed_by_project_id)
values ('main', '');

alter table app_store.application_groups add column
    curator text not null default 'main' references app_store.curators(id);

alter table app_store.applications add column
    curator text not null default 'main' references app_store.curators(id);

alter table app_store.categories add column
    curator text not null default 'main' references app_store.curators(id);

alter table app_store.tools add column
    curator text not null default 'main' references app_store.curators(id);
