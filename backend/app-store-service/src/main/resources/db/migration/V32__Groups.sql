create table if not exists app_store.application_groups (
    id serial primary key,
    title text not null unique,
    logo bytea,
    description text,
    default_name text,
    default_version text,
    foreign key (default_name, default_version) references app_store.applications(name, version)
);

alter table app_store.applications add column if not exists group_id int references app_store.application_groups(id);
alter table app_store.applications add column if not exists flavor_name text;

create table if not exists app_store.group_tags (
    group_id int references application_groups(id),
    tag_id int references tags(id),
    primary key (group_id, tag_id)
);

create table if not exists app_store.sections (
    id serial primary key,
    title text not null,
    order_index int not null,
    page text not null
);

create table if not exists app_store.section_featured_items (
    section_id int references sections(id),
    group_id int references app_store.application_groups(id),
    order_index int not null,
    primary key (section_id, group_id)
);

create table if not exists app_store.section_tags (
    section_id int references sections(id),
    tag_id int references tags(id),
    primary key (section_id, tag_id)
);

drop table if exists app_store.overview;