package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__Accounting() = MigrationScript("V1__Accounting") { session ->
    session.prepareStatement(
        // language=postgresql
        """
            create table accounting_tracked_resource(
                resource_id int8 primary key,
                time_tracked int8 not null default 0,
                last_call int8 not null default 0
            )
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        // language=postgresql
        """
            create table accounting_tracked_workspace(
                workspace_reference text not null,
                workspace_is_user bool not null,
                category text not null,
                time_tracked int8 not null default 0,
                last_call int8 not null default 0,
                primary key (workspace_reference, workspace_is_user, category)
            )
        """
    ).useAndInvokeAndDiscard()
}
