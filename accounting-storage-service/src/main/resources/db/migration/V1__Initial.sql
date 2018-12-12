set search_path to accounting_storage;

create sequence accounting_storage.hibernate_sequence
    start 1
    increment 1;

create table accounting_storage.storage_usage_for_user (
    id int8 not null,
    date date,
    usage int8 not null,
    "user" varchar(255),
    primary key (id)
);

create index on accounting_storage.storage_usage_for_user ("user");
