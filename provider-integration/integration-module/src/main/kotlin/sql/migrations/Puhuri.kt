package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__Puhuri() = MigrationScript("V1__Puhuri") { session ->
    session.prepareStatement(
        """
            create table puhuri_connections(
                ucloud_identity text primary key,
                puhuri_identity text
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        """
            create table puhuri_unknown_users(
                ucloud_identity text,
                ucloud_project text,
                ucloud_project_role text,
                primary key(ucloud_identity, ucloud_project)
            );
        """
    ).useAndInvokeAndDiscard()
}
