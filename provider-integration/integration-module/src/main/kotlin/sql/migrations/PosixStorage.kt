package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__PosixStorage() = MigrationScript("V1__PosixStorage") { session ->
    session.prepareStatement(
        """
            create table posix_storage_scan(
                id text primary key,
            	last_charged_period_end timestamp not null
            );
        """
    ).useAndInvokeAndDiscard()
}