drop table if exists "storage".scans;

create table "storage".scans(
    date_string text not null,
    entity text not null,
    entity_type text not null,
    primary key (date_string, entity, entity_type)
);
