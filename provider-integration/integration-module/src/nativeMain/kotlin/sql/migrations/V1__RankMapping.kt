package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__SessionMapping(): MigrationScript = MigrationScript("V1__SessionMapping") { conn ->
    conn.prepareStatement(
        //language=SQLite
        //TODO: using not unique token for now, this is related to 2 tokens being generated and some logic in the openInteractiveSession
        // token text primary key,
        """
            create table session_mapping(
                pkey integer primary key autoincrement,
                token text not null,
                rank integer not null,
                ucloud_id text not null,
                ts datetime default current_timestamp not null
            )
        """
    ).useAndInvokeAndDiscard()
}
