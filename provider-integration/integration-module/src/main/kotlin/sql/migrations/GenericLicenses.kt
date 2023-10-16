package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__GenericLicenses() = MigrationScript("V1__GenericLicenses") { session ->
    session.prepareStatement(
        //language=postgresql
        """
            create table generic_license_servers(
                name text not null,
                category text not null,
                address text,
                port int,
                license text,
                primary key (name, category)
            );
        """
    ).useAndInvokeAndDiscard()
}

fun V2__GenericLicenses() = MigrationScript("V2__GenericLicenses") { session ->
    session.prepareStatement(
        //language=postgresql
        """
            create table generic_license_instances(
                id text not null primary key,
                category text not null,
                owner text not null
            );
        """
    ).useAndInvokeAndDiscard()
}
