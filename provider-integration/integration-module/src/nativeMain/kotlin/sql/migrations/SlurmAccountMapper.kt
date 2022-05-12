package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.migrations.*
import dk.sdu.cloud.sql.*

fun V1__SlurmAccountMapper(): MigrationScript = MigrationScript("V1__SlurmAccountMapper") { conn ->
    conn.prepareStatement(
        """
            create table slurm_account_mapper(
                username text,
                project_id text,
                category text not null,
                partition text not null
            )
        """
    ).useAndInvokeAndDiscard()
}

