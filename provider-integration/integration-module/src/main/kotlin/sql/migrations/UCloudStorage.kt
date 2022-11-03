package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
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
    session.prepareStatement(
        //language=postgresql
        """
            create table ucloud_storage_timestamps(
                name text primary key,
                last_run bigint
            );
        """
    )
}
