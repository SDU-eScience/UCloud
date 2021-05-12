package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationHandler

fun loadMigrations(migrationHandler: MigrationHandler) {
    migrationHandler.addScript(V1__TicketConnector())
    migrationHandler.addScript(V2__UserMapping())
}
