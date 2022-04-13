delete from file_orchestrator.sync_devices where true;
delete from file_orchestrator.sync_folders where true;
delete from provider.resource_update
where
    resource in (
        select id
        from provider.resource
        where product in (
            select id from accounting.products where name = 'u1-sync'
        )
    );
delete from provider.resource_acl_entry
where
    resource_id in (
        select id
        from provider.resource
        where product in (
            select id from accounting.products where name = 'u1-sync'
        )
    );
delete from provider.resource where product in (select id from accounting.products where name = 'u1-sync');
delete from accounting.products where name = 'u1-sync';
delete from accounting.product_categories where category = 'u1-sync';

drop function if exists file_orchestrator.remove_sync_folders(ids bigint[]);
drop function if exists file_orchestrator.sync_device_to_json(device_in file_orchestrator.sync_devices);
drop function if exists file_orchestrator.sync_folder_to_json(sync_in file_orchestrator.sync_with_dependencies);

drop type file_orchestrator.sync_with_dependencies;

drop table if exists file_orchestrator.sync_devices;
drop table if exists file_orchestrator.sync_folders;
drop table if exists file_ucloud.sync_devices;
drop table if exists file_ucloud.sync_folders;

