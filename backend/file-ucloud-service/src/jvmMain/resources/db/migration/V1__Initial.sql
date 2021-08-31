create schema if not exists storage;
create table storage.quotas
(
    path           text   not null primary key,
    quota_in_bytes bigint not null
);

create table storage.quota_allocation
(
    from_directory text   not null,
    to_directory   text   not null,
    allocation     bigint not null,
    constraint quota_allocation_pkey
        primary key (from_directory, to_directory)
);

create table storage.metadata
(
    path           text      not null,
    path_moving_to text,
    last_modified  timestamp not null,
    username       text      not null,
    type           text      not null,
    data           jsonb     not null,
    constraint metadata_pkey
        primary key (path, type, username)
);

create index metadata_path on storage.metadata (path text_pattern_ops);
create index metadata_username on storage.metadata (username);
create index metadata_type on storage.metadata (type);
