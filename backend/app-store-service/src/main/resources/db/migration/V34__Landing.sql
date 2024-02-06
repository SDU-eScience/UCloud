drop table if exists app_store.application_logos;
drop table if exists app_store.application_tags;

drop table if exists app_store.tool_logos;

drop table if exists app_store.section_tags;
drop table if exists app_store.section_featured_items;
drop table if exists app_store.sections;

alter table if exists app_store.tags rename to categories;
alter table if exists app_store.group_tags rename to category_items;

alter table app_store.categories add column if not exists priority int default 10000;

create table if not exists app_store.top_picks(
    application_name text,
    group_id int references app_store.application_groups(id),
    description text not null,
    priority int not null primary key
);

create table if not exists app_store.spotlights(
    id serial not null primary key,
    title text not null,
    description text not null,
    active bool not null default false
);

drop table if exists app_store.spotlight_items;
create table if not exists app_store.spotlight_items(
    spotlight_id int not null references app_store.spotlights(id),
    application_name text,
    group_id int references app_store.application_groups(id),
    description text not null,
    priority int not null primary key
);

drop table if exists app_store.carrousel_items;
create table if not exists app_store.carrousel_items(
    title text not null,
    body text not null,
    image_credit text not null,
    linked_application text,
    linked_group int references app_store.application_groups(id),
    linked_web_page text,
    image bytea not null,
    priority int primary key
);
