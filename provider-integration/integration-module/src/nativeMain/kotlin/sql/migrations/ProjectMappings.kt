package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__ProjectMapping(): MigrationScript = MigrationScript("V1__ProjectMapping") { session ->
    session.prepareStatement(
        //language=SQLite
        """
            create table project_mapping(
                ucloud_id text primary key not null,
                local_id text not null
            )
        """
    ).useAndInvokeAndDiscard()
}
