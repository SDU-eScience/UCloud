package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__JobMapping(): MigrationScript = MigrationScript("V1__JobMapping") { conn ->
    conn.prepareStatement(
        //language=SQLite
        """
            create table job_mapping(
                ucloud_id text primary key,
                local_id text not null,
                partition text not null,
                status integer default 1 not null,
                lastknown text not null,
                ts datetime default current_timestamp not null
            )
        """
    ).useAndInvokeAndDiscard()
}
