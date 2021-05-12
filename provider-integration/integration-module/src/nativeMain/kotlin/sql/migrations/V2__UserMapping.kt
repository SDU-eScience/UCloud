package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V2__UserMapping(): MigrationScript = MigrationScript("V2__UserMapping") { conn ->
    conn.prepareStatement(
        //language=SQLite
        """
            create table user_mapping(
                ucloud_id text primary key,
                local_identity text not null unique 
            )
        """
    ).useAndInvokeAndDiscard()
}
