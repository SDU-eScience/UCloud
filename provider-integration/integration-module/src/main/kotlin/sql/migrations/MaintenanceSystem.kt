package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.useAndInvokeAndDiscard

fun V1__Maintenance() = MigrationScript("V1__Maintenance") { session ->
    session.prepareStatement(
        """
            create table maintenance_periods(
                id bigserial primary key,
                description text not null,
                availability text not null,
                product_matcher text not null,
                starts_at bigint not null,
                ends_at bigint default null
            )
        """
    ).useAndInvokeAndDiscard()
}
