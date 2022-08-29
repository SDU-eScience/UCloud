package dk.sdu.cloud.sql.migrations

import dk.sdu.cloud.sql.MigrationHandler

fun loadMigrations(migrationHandler: MigrationHandler) {
    // NOTE(Dan): Do not re-order these. You should always add new migrations at the bottom of this function.
    migrationHandler.addScript(V1__TicketConnector())
    migrationHandler.addScript(V1__JobMapping())
    migrationHandler.addScript(V2__UserMapping())
    migrationHandler.addScript(V1__FileDownloadSessions())
    migrationHandler.addScript(V1__FileUploadSessions())
    migrationHandler.addScript(V1__Tasks())
    migrationHandler.addScript(V1__ProjectMapping())
    migrationHandler.addScript(V1__SimpleProjectPluginInitial())
    migrationHandler.addScript(V2__SimpleProjectPlugin())
    migrationHandler.addScript(V2__SessionMapping())
    migrationHandler.addScript(V3__SlurmAccountMapper())
    migrationHandler.addScript(V4__SlurmAccounting())
    migrationHandler.addScript(V5__FixSlurmAccountMapper())
    migrationHandler.addScript(V1__MessageSigning())
    migrationHandler.addScript(V1__Puhuri())
    migrationHandler.addScript(V1__UCloudStorage())
    migrationHandler.addScript(V1__UCloudCompute())
    migrationHandler.addScript(V1__ComputeSessions())
    migrationHandler.addScript(V2__ComputeSessions())
    migrationHandler.addScript(V1__GenericLicenses())
}
