package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__ComputeSessions() = MigrationScript("V1__ComputeSessions") { session ->
    session.prepareStatement(
        """
            create table compute_sessions(
                session text primary key,
                session_type text not null,
                job_id text not null,
                job_rank int not null,
                plugin_name text not null,
                plugin_data text not null,
                created_at datetime default current_timestamp not null
            );
        """
    ).useAndInvokeAndDiscard()
}
