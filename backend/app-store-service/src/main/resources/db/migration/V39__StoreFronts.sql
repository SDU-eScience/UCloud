create table app_store.repositories(
    id serial primary key,
    title text not null,
    is_public bool not null default false,
    managed_by_project_id text not null references project.projects(id)
);

create table app_store.store_fronts(
    id serial primary key,
    title text not null
);

create table app_store.provider_store_fronts(
    project_id text not null references project.projects(id),
    store_front_id int not null references app_store.store_fronts(id)
);

create table app_store.repository_subscriptions(
    store_front_id int not null references app_store.store_fronts(id),
    repository_id text not null references app_store.repositories(id)
);

alter table app_store.top_picks add column
    store_front_id int not null references app_store.store_fronts(id);

alter table app_store.spotlights add column
    store_front_id int not null references app_store.store_fronts(id);

alter table app_store.carrousel_items add column
    store_front_id int not null references app_store.store_fronts(id);

alter table app_store.application_groups add column
    repository_id int references app_store.repositories(id);
