create table synchronized_folders (
    id          varchar(36) not null primary key,
    device_id   varchar(64) not null,
    path        text        not null,
    access_type varchar(20) not null default 'SEND_ONLY',
    user_id     text        not null
);

create table user_devices (
    device_id   varchar(64)  not null primary key,
    user_id     text         not null
);

alter table synchronized_folders
    add constraint unique_synchronized_folders unique (path, user_id);
