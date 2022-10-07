package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__ExtensionFailure() = MigrationScript("V1__ExtensionFailure") { session ->
    session.prepareStatement(
        //language=postgresql
        """
            create table extension_failure(
                ts bigint not null,
                id bigserial primary key,
                request text not null,
                extension_path text not null,
                stdout text not null,
                stderr text not null,
                status_code int not null,
                uid int not null,
                foo float4
            )
        """
    ).useAndInvokeAndDiscard()
}
