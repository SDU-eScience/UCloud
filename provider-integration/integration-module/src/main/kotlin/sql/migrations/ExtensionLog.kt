package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__ExtensionLog() = MigrationScript("V1__ExtensionLog") { session ->
    session.prepareStatement(
        //language=postgresql
        """
            create table extension_log(
                timestamp timestamptz not null,
                id bigserial primary key,
                request text not null,
                extension_path text not null,
                stdout text not null,
                stderr text not null,
                status_code int not null,
                success bool not null,
                uid int not null
            )
        """
    ).useAndInvokeAndDiscard()
}
