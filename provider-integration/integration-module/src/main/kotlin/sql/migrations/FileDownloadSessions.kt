package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__FileDownloadSessions(): MigrationScript = MigrationScript("V1__FileDownloadSessions") { conn ->
    conn.prepareStatement(
        //language=postgresql
        """
            create table file_download_sessions(
                session text primary key,
                plugin_name text not null,
                plugin_data text not null
            )
        """
    ).useAndInvokeAndDiscard()
}

fun V2__FileDownloadSessions(): MigrationScript = MigrationScript("V2__FileDownloadSessions") { conn ->
    conn.prepareStatement(
        // language=postgresql
        """
            alter table file_download_sessions add column owned_by int not null default 0;
        """
    ).useAndInvokeAndDiscard()
}
