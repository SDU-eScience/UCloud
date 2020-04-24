create sequence hibernate_sequence start with 1 increment by 1;

create table downtimes (
    id bigint not null,
    end_time timestamp,
    start_time timestamp,
    text TEXT,
    primary key (id)
);