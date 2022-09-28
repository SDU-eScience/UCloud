package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__Puhuri() = MigrationScript("V1__Puhuri") { session ->
    session.prepareStatement(
        //language=postgresql
        """
            create table puhuri_connections(
                ucloud_identity text primary key,
                puhuri_identity text
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
            create table puhuri_project_users(
                ucloud_identity text not null,
                ucloud_project text not null,
                puhuri_identity text not null,
                ucloud_project_role text,
                synchronized_to_puhuri bool,
                primary key(ucloud_identity, ucloud_project)
            );
        """
    ).useAndInvokeAndDiscard()

    session.prepareStatement(
        //language=postgresql
        """
            create table puhuri_allocations(
                allocation_id text primary key,
                balance bigint not null,
                product_type text not null,
                synchronized_to_puhuri bool
            );
        """
    ).useAndInvokeAndDiscard()
}
