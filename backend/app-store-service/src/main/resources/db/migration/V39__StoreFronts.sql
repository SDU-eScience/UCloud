create table app_store.repositories(
    id serial primary key,
    title text not null,
    is_public bool not null default false,
    managed_by_project_id text not null
);

insert into app_store.repositories(id, title, is_public, managed_by_project_id)
values (1, 'Main repository', false, '');


create table app_store.store_fronts(
    id serial primary key,
    title text not null
);

create table app_store.provider_store_fronts(
    project_id text not null,
    store_front_id int not null references app_store.store_fronts(id)
);

create table app_store.repository_subscriptions(
    store_front_id int not null references app_store.store_fronts(id),
    repository_id int not null references app_store.repositories(id)
);

alter table app_store.top_picks add column
    store_front_id int not null references app_store.store_fronts(id);

alter table app_store.spotlights add column
    store_front_id int not null references app_store.store_fronts(id);

alter table app_store.carrousel_items add column
    store_front_id int not null references app_store.store_fronts(id);

alter table app_store.application_groups add column
    repository_id int not null default 1 references app_store.repositories(id);

alter table app_store.applications add column
    repository int not null default 1 references app_store.repositories(id);
