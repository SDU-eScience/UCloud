package dk.sdu.cloud.file.migration

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.services.acl.Metadata
import dk.sdu.cloud.file.services.acl.MetadataDao
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class PermissionMigration(
    private val db: AsyncDBSessionFactory,
    private val metadata: MetadataDao
) {
    data class PermissionRow(val path: String, val username: String, val permission: String)
    private data class AclMetadata(
        val permissions: Set<AccessRight>
    )

    suspend fun runDataMigration() {
        val permissions = ArrayList<PermissionRow>()

        db.withTransaction { session ->
            session.sendQuery(
                """
                    declare c no scroll cursor for 
                        select path, username, permission
                        from storage.permissions
                """
            )

            while (true) {
                val rows = session.sendQuery("fetch forward 100 from c").rows
                rows.forEach { row ->
                    permissions.add(
                        PermissionRow(
                            row.getString(0)!!,
                            row.getString(1)!!,
                            row.getString(2)!!
                        )
                    )
                }

                if (rows.isEmpty()) break
            }
        }

        db.withTransaction { session ->
            permissions
                .groupBy { it.username to it.path }
                .forEach { (usernameAndPath, rows) ->
                    val (username, path) = usernameAndPath
                    val meta = AclMetadata(rows.map { AccessRight.valueOf(it.permission) }.toSet())
                    metadata.createMetadata(session, Metadata(path, "acl", username, defaultMapper.valueToTree(meta)))
                }
        }
    }
}
