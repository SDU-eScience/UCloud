package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__Tasks(): MigrationScript = MigrationScript("V1__Tasks") { conn ->
    conn.prepareStatement(
        //language=SQLite
        """
            create table tasks(
                title text not null,
                ucloud_task_id text not null,
                local_identity text not null
            )
        """
    ).useAndInvokeAndDiscard()
}
