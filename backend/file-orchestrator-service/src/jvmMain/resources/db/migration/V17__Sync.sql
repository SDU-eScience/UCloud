drop table if exists file_orchestrator.sync_devices cascade;
drop table if exists file_orchestrator.sync_folders cascade;

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
    collection bigint not null references provider.resource(id),
    sub_path text not null,
    status_permission text not null,
    remote_device_id text default null
);

drop type if exists file_orchestrator.sync_with_dependencies cascade ;
create type file_orchestrator.sync_with_dependencies as
(
    resource bigint,
    folder file_orchestrator.sync_folders
);

create or replace function file_orchestrator.sync_folder_to_json(
    sync_in file_orchestrator.sync_with_dependencies
) returns jsonb language sql as $$
select jsonb_build_object(
    'specification', jsonb_build_object(
        'path', '/' || (sync_in.folder).collection || (sync_in.folder).sub_path
    ),
    'status', jsonb_build_object(
        'remoteDevice', (sync_in.folder).remote_device_id,
        'permission', (sync_in.folder).status_permission
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
