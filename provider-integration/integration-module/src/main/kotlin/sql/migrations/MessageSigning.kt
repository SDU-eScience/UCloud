package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__MessageSigning(): MigrationScript = MigrationScript("V1__MessageSigning") { conn ->
    conn.prepareStatement(
        //language=postgresql
        """
            create table message_signing_key(
                id serial primary key,
                ts timestamp default current_timestamp not null,
                is_key_active bool default false,
                public_key text not null,
                ucloud_user text not null
            )
        """
    ).useAndInvokeAndDiscard()
}
