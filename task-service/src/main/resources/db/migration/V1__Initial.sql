create sequence hibernate_sequence start with 1 increment by 1;

create table subscriptions
(
    id        bigint  not null,
    hostname  varchar(255),
    last_ping timestamp,
    port      integer not null,
    username  varchar(255),
    primary key (id)
);

create table tasks
(
    job_id         varchar(255) not null,
    complete       boolean      not null,
    created_at     timestamp,
    modified_at    timestamp,
    owner          varchar(255),
    processor      varchar(255),
    status_message varchar(65536),
    title          varchar(65536),
    primary key (job_id)
);
