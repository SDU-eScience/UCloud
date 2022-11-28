package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__Events() = MigrationScript("V1__Events") { session ->
    session.prepareStatement(
        """
            create schema events;
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        """
            create table events.projects_to_ignore(project_id text not null primary key)
        """
    ).useAndInvokeAndDiscard()
}
