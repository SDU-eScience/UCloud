create table file_ucloud.sync_devices(
    id bigint primary key,
    device_id varchar(64) not null,
    user_id text not null
);

create table file_ucloud.sync_folders (
    id bigint primary key,
    device_id varchar(64) not null,
    path text not null,
    sync_type varchar(20) not null default 'SEND_ONLY',
    user_id text not null,
    unique(path, user_id)
);
