drop table if exists file_ucloud.upload_sessions;
create table file_ucloud.upload_sessions(
    id text not null primary key,
    relative_path text not null,
    last_update timestamp
);
