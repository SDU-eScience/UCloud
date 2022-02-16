package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__RankMapping(): MigrationScript = MigrationScript("V1__RankMapping") { conn ->
    conn.prepareStatement(
        //language=SQLite
        //TODO: finish schema
        """
            create table rank_mapping(
                token text primary key,
                local_id text not null,
                rank text not null
            )
        """
    ).useAndInvokeAndDiscard()
}
