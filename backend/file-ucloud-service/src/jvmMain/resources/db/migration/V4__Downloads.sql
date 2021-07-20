create table if not exists file_ucloud.download_sessions(
    id text primary key,
    relative_path text not null,
    last_update timestamptz default now()
);
