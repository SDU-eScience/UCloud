create schema if not exists storage;
create table if not exists storage.quotas
(
    path           text   not null primary key,
    quota_in_bytes bigint not null
);

create table if not exists storage.quota_allocation
(
    from_directory text   not null,
    to_directory   text   not null,
    allocation     bigint not null,
    constraint quota_allocation_pkey
        primary key (from_directory, to_directory)
);

create table if not exists storage.metadata
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

create index if not exists metadata_path on storage.metadata (path text_pattern_ops);
create index if not exists metadata_username on storage.metadata (username);
create index if not exists metadata_type on storage.metadata (type);
