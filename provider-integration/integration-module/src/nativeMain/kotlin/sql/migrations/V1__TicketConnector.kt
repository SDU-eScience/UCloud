package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.invokeAndDiscard
import dk.sdu.cloud.sql.use

fun V1__TicketConnector() = MigrationScript("V1__TicketConnector") { conn ->
    conn.prepareStatement(
        //language=SQLite
        """
            create table ticket_connections(
                ticket text primary key,
                ucloud_id text not null,
                created_at timestamp not null,
                completed_at timestamp
            )
        """
    ).use { it.invokeAndDiscard() }
}
