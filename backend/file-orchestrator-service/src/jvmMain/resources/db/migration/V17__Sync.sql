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

create or replace function file_orchestrator.remove_sync_folders(
    ids bigint[]
) returns void language sql as $$
    delete from file_orchestrator.sync_folders where resource in (select unnest(ids));
    delete from provider.resource_acl_entry where resource_id in (select unnest(ids));
    delete from provider.resource_update where resource in (select unnest(ids));
    delete from provider.resource where id in (select unnest(ids));
$$;
