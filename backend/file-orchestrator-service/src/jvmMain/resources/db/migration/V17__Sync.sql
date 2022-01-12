create table file_orchestrator.sync_devices(
    resource bigint references provider.resource(id) primary key,
    device_id varchar(64) not null
);

create or replace function file_orchestrator.sync_device_to_json(
    device_in file_orchestrator.sync_devices
) returns jsonb language sql as $$
select jsonb_build_object(
    'specification', jsonb_build_object(
        'deviceId', device_in.device_id
    )
);
$$;

create table file_orchestrator.sync_folders (
    resource bigint references provider.resource(id) primary key,
    device_id   varchar(64) null default null,
    path        text        not null,
    sync_type varchar(20) not null default 'SEND_ONLY'
);

create or replace function file_orchestrator.sync_folder_to_json(
    folder_in file_orchestrator.sync_folders
) returns jsonb language sql as $$
select jsonb_build_object(
    'specification', jsonb_build_object(
        'path', folder_in.path
    ),
    'status', jsonb_build_object(
        'deviceId', folder_in.device_id,
        'syncType', folder_in.sync_type
    )
);
$$;

