package dk.sdu.cloud.plugins

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import io.ktor.http.*

object ProjectMapper {
    fun registerProjectMapping(
        ucloudProject: String,
        localProject: String,
        db: DBContext = dbConnection,
    ) {
        db.withSession { session ->
            session
                .prepareStatement(
                    //language=SQLite
                    """
                        insert into project_mapping(ucloud_id, local_id) values (:ucloud_id, :local_id)
                    """
                )
                .useAndInvokeAndDiscard(
                    prepare = {
                        bindString("ucloud_id", ucloudProject)
                        bindString("local_id", localProject)
                    }
                )
        }
    }

    fun mapUCloudToLocal(
        ucloudProject: String,
        db: DBContext = dbConnection,
    ): String {
        var result: String? = null
        db.withSession { session ->
            session.prepareStatement(
                //language=SQLite
                """
                    select local_id
                    from project_mapping
                    where ucloud_id = :ucloud_id
                """
            ).useAndInvoke(
                prepare = {
                    bindString("ucloud_id", ucloudProject)
                },
                readRow = { row ->
                    result = row.getString(0)
                }
            )
        }

        return result ?: throw RPCException("Unknown project", HttpStatusCode.NotFound)
    }

    fun mapLocalToUCloud(
        localProject: String,
        db: DBContext = dbConnection,
    ): List<String> {
        val result = ArrayList<String>()
        db.withSession { session ->
            session.prepareStatement(
                //language=SQLite
                """
                    select ucloud_id
                    from project_mapping
                    where local_id = :local_id
                """
            ).useAndInvoke(
                prepare = {
                    bindString("local_id", localProject)
                },
                readRow = { row ->
                    result.add(row.getString(0)!!)
                }
            )
        }

        return result
    }

    fun clearMappingByUCloudProject(
        ucloudProject: String,
        db: DBContext = dbConnection,
    ) {
        db.withSession { session ->
            session.prepareStatement(
                //language=SQLite
                """
                    delete from project_mapping
                    where ucloud_id = :ucloud_id
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("ucloud_id", ucloudProject)
                }
            )
        }
    }

    fun clearMappingByLocalProject(
        localProject: String,
        db: DBContext = dbConnection,
    ) {
        db.withSession { session ->
            session.prepareStatement(
                //language=SQLite
                """
                    delete from project_mapping
                    where local_id = :local_id
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("local_id", localProject)
                }
            )
        }
    }
}
