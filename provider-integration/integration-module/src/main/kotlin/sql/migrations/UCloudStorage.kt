package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.invokeAndDiscard
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__UCloudStorage() = MigrationScript("V1__UCloudStorage") { session ->
    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_storage_quota_locked(
                scan_id text,
                category text default '' not null,
                username text,
                project_id text
            );            
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_storage_storage_scan(
            	last_system_scan timestamp not null
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_storage_tasks(
            	id text not null primary key,
            	request_name text not null,
            	requirements text,
            	request text,
            	progress text,
            	last_update timestamp,
            	processor_id text,
            	complete boolean default false
            );
        """
    ).useAndInvokeAndDiscard()
}

fun V2__UCloudStorage() = MigrationScript("V2__UCloudStorage") { session ->
    // Forgot to run the script which were once here.
}

fun V3__UCloudStorage() = MigrationScript("V3__UCloudStorage") { session ->
    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_storage_timestamps(
                name text primary key,
                last_run bigint
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_storage_drives(
                collection_id bigint primary key,
                local_reference text,
                project text,
                type text not null,
                system text not null,
                in_maintenance_mode bool default false
            )
        """
    ).useAndInvokeAndDiscard()
}

fun V4__UCloudStorage() = MigrationScript("V4__UCloudStorage") { session ->
    session.prepareStatement(
        // language=postgresql
        """
            alter table ucloud_storage_drives add column size_in_bytes int8 default 0
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        // language=postgresql
        """
            alter table ucloud_storage_drives add column drive_owner text default null
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        // language=postgresql
        """
            alter table ucloud_storage_drives add column drive_owner_is_user bool default null
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        // language=postgresql
        """
            alter table ucloud_storage_drives add column product_name text default null
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        // language=postgresql
        """
            alter table ucloud_storage_drives add column product_category text default null
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        // language=postgresql
        """
            create table ucloud_storage_scans(
                drive_id int8 primary key,
                last_scan int8 not null
            );
        """
    ).useAndInvokeAndDiscard()
}
