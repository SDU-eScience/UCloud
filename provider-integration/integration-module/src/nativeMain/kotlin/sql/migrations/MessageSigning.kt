package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__MessageSigning(): MigrationScript = MigrationScript("V1__MessageSigning") { conn ->
    conn.prepareStatement(
        """
            create table message_signing_key(
                id integer primary key autoincrement,
                ts datetime default current_timestamp not null,
                is_key_active bool default false,
                public_key text not null,
                ucloud_user text not null
            )
        """
    ).useAndInvokeAndDiscard()
}
