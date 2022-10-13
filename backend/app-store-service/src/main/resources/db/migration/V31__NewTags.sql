create table app_store.tags(
    id serial primary key,
    tag text not null
);

create unique index tag_unique on app_store.tags (lower(tag));

insert into app_store.tags (id, tag) values (0, 'temporary');

insert into app_store.tags (tag)
    select distinct tag from app_store.application_tags
    on conflict do nothing
;


alter table app_store.application_tags
    add column tag_id int references app_store.tags(id) not null default 0;

update app_store.application_tags set tag_id = (
    select id from app_store.tags where lower(tag) = lower(application_tags.tag) limit 1
);

delete from app_store.tags where id = 0;

alter table app_store.application_tags
    drop column tag;

create unique index on app_store.application_tags(tag_id,application_name);