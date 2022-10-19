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

delete from app_store.application_tags a using app_store.application_tags b
    where a.id < b.id and a.application_name = b.application_name and a.tag_id = b.tag_id;

create unique index on app_store.application_tags(tag_id,application_name);

alter table app_store.application_tags drop column id;

create table app_store.overview (
    reference_type text not null,
    reference_id text not null,
    order_id int not null,
    rows int default 1,
    columns int default 7
);

create sequence app_store.overview_order_sequence start 1 increment 1;

insert into app_store.overview (reference_type, reference_id, order_id)
    select 'TAG', id, nextval('overview_order_sequence') from app_store.tags where tag in (
        'Featured',
        'Engineering',
        'Data Analytics',
        'Social Science',
        'Applied Science',
        'Natural Science',
        'Development',
        'Virtual Machines',
        'Digital Humanities',
        'Health Science',
        'Bioinformatics'
    );

update app_store.overview set rows = 3 where reference_type = 'TAG' and
    cast(reference_id as int) = (select id from tags where tag = 'Featured');

update app_store.tags set tag = 'Development' where tag = 'development';

update app_store.overview set order_id = 1 where reference_type = 'TAG' and
    cast(reference_id as int) = (select id from tags where tag = 'Featured');

update app_store.overview set order_id = 2 where reference_type = 'TAG' and
    cast(reference_id as int) = (select id from tags where tag = 'Engineering');

update app_store.overview set order_id = 3 where reference_type = 'TAG' and
    cast(reference_id as int) = (select id from tags where tag = 'Data Analytics');

update app_store.overview set order_id = 4 where reference_type = 'TAG' and
    cast(reference_id as int) = (select id from tags where tag = 'Social Science');

update app_store.overview set order_id = 5 where reference_type = 'TAG' and
    cast(reference_id as int) = (select id from tags where tag = 'Applied Science');

update app_store.overview set order_id = 6 where reference_type = 'TAG' and
    cast(reference_id as int) = (select id from tags where tag = 'Natural Science');

update app_store.overview set order_id = 7 where reference_type = 'TAG' and
    cast(reference_id as int) = (select id from tags where tag = 'Development');

update app_store.overview set order_id = 8 where reference_type = 'TAG' and
        cast(reference_id as int) = (select id from tags where tag = 'Virtual Machines');

update app_store.overview set order_id = 9 where reference_type = 'TAG' and
        cast(reference_id as int) = (select id from tags where tag = 'Digital Humanities');

update app_store.overview set order_id = 10 where reference_type = 'TAG' and
        cast(reference_id as int) = (select id from tags where tag = 'Health Science');

update app_store.overview set order_id = 11 where reference_type = 'TAG' and
        cast(reference_id as int) = (select id from tags where tag = 'Bioinformatics');

insert into app_store.overview (reference_type, reference_id, order_id) values
    ('TOOL', 'BEDTools', nextval('overview_order_sequence')),
    ('TOOL', 'Cell Ranger', nextval('overview_order_sequence')),
    ('TOOL', 'HOMER', nextval('overview_order_sequence')),
    ('TOOL', 'Kallisto', nextval('overview_order_sequence')),
    ('TOOL', 'MACS2', nextval('overview_order_sequence')),
    ('TOOL', 'SAMtools', nextval('overview_order_sequence')),
    ('TOOL', 'Salmon', nextval('overview_order_sequence')),
    ('TOOL', 'Seqtk', nextval('overview_order_sequence')),
    ('TOOL', 'Space Ranger', nextval('overview_order_sequence')),
    ('TOOL', 'nf-core', nextval('overview_order_sequence'))
;
