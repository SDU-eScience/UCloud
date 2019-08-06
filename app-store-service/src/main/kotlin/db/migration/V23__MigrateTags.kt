package db.migration

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Statement

class V23__MigrateMetadata : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val connection = context.connection
        connection.autoCommit = false

        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT name, version, tags FROM applications;").use { row ->
                while (row.next()) {
                    val name = row.getString(1)
                    val version = row.getString(2)
                    val tagsFromDB = row.getString(3)

                    val parsedTags = defaultMapper.readValue<List<String>>(tagsFromDB)

                    parsedTags.forEach { tag ->
                        connection.createStatement().use { statement ->

                            val exists = connection.prepareStatement(
                                "SELECT * FROM application_tags WHERE (tag=? AND application_name=? AND application_version=?)"
                            ).apply {
                                setString(1,tag)
                                setString(2,name)
                                setString(3,version)
                            }.executeQuery().next()

                            if (!exists) {
                                val id = connection.prepareStatement(
                                    "SELECT nextval('hibernate_sequence')"
                                ).executeQuery()

                                id.next()

                                connection.prepareStatement(
                                    "INSERT INTO application_tags (id,tag,application_name,application_version) VALUES (?,?,?,?);"
                                )
                                    .apply {
                                        setInt(1, id.getInt(1))
                                        setString(2, tag)
                                        setString(3, name)
                                        setString(4, version)
                                    }
                                    .executeUpdate()
                            }
                        }
                    }
                }
            }
        }
    }
}
