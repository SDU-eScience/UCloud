package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationScript
import dk.sdu.cloud.sql.invokeAndDiscard

fun V1__ActivitySystem() = MigrationScript("V1__ActivitySystem") { session ->
    session.prepareStatement(
        // language=postgresql
        """
            create table activity_system(
                reference_is_user bool not null,
                workspace_reference text not null,
                last_activity int8 not null,
                primary key (reference_is_user, workspace_reference)
            );
        """
    ).invokeAndDiscard()
}
