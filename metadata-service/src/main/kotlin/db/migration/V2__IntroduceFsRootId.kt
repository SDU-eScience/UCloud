package db.migration

import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.metadata.FlywayMigrationContext.cloud
import dk.sdu.cloud.metadata.api.MetadataServiceDescription
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.FindByPath
import kotlinx.coroutines.experimental.runBlocking
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import java.sql.Connection

class V2__IntroduceFsRootId : JdbcMigration {
    override fun migrate(connection: Connection) {
        connection.schema = MetadataServiceDescription.name

        log.info("Adding fs_root_id column to projects table")

        connection
            .prepareStatement("alter table projects add column fs_root_id varchar(256);")
            .use { it.execute() }

        data class ProjectRow(val id: Long, val fsRoot: String)

        val projectRows = ArrayList<ProjectRow>()

        connection.prepareStatement("select id, fs_root from projects;").use {
            val row = it.executeQuery()

            while (row.next()) {
                val id = row.getLong(0)
                val fsRoot = row.getString(1)

                projectRows.add(ProjectRow(id, fsRoot))
            }
        }

        log.info("Found ${projectRows.size} projects")

        /*
        // This code definitely didn't work. We would need a way of acting as the correct user. Which we don't!

        val deleteBatch = connection.prepareStatement("delete from projects where id = ?")
        val updateBatch = connection.prepareStatement("update projects set fs_root_id = ? where id = ?")

        runBlocking {
            projectRows.forEach { project ->
                val stat =
                    FileDescriptions.stat.call(FindByPath(project.fsRoot), cloud).let { it as? RESTResponse.Ok }?.result

                if (stat == null) {
                    log.info("Deleting project $project. Could not find it in FS")
                    deleteBatch.setLong(1, project.id)
                    deleteBatch.addBatch()
                } else {
                    log.info("Updating project $project with fsRootId ${stat.inode}")
                    updateBatch.setString(1, stat.inode)
                    updateBatch.setLong(2, project.id)
                }
            }
        }

        deleteBatch.use { it.executeBatch() }
        updateBatch.use { it.executeBatch() }
        */

        connection
            .prepareStatement("alter table projects add constraint uk_projects_fs_root_id unique(fs_root_id)")
            .use { it.execute() }

        connection
            .prepareStatement("create index on projects (fs_root_id)")
            .use { it.execute() }
    }

    companion object : Loggable {
        override val log = logger()
    }
}