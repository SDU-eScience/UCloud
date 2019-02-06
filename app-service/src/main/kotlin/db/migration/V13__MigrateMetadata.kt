package db.migration

import dk.sdu.cloud.client.defaultMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V13__MigrateMetadata : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val connection = context.connection
        connection.autoCommit = false

        val newColumns = listOf(
            "authors" to "jsonb",
            "tags" to "jsonb",
            "title" to "varchar(256)",
            "description" to "text",
            "website" to "varchar(1024)"
        )

        newColumns.forEach { (colName, colType) ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    //language=sql
                    """
                    alter table applications
                      add column $colName $colType
                    """.trimIndent()
                )
            }
        }

        connection.createStatement().use { statement ->
            statement.execute("create index on applications using gin (tags);")
        }

        connection.createStatement().use { statement ->
            statement.executeQuery("select name, version, application from applications;").use { row ->
                while (row.next()) {
                    val name = row.getString(1)
                    val version = row.getString(2)
                    val applicationJson = row.getString(3)

                    val application = defaultMapper.readTree(applicationJson)

                    val tags = ArrayList<String>()
                    connection
                        .prepareStatement(
                            //language=sql
                            """
                                select tag
                                from application_tags
                                where
                                  application_name=? and
                                  application_version=?
                            """.trimIndent()
                        )
                        .apply {
                            setString(1, name)
                            setString(2, version)
                        }
                        .executeQuery()
                        .use { tag ->
                            while (tag.next()) {
                                tags.add(tag.getString(1))
                            }
                        }

                    val authors = application["authors"].map { it.asText() }
                    val title = application["title"].asText()
                    val description = application["description"].asText()
                    val website = application["website"].takeIf { !it.isNull }?.asText()

                    connection
                        .prepareStatement(
                            //language=sql
                            """
                                update applications
                                set
                                    authors=?::jsonb,
                                    title=?,
                                    description=?,
                                    website=?,
                                    tags=?::jsonb
                                where
                                    name=? and
                                    version=?
                            """.trimIndent()
                        )
                        .apply {
                            setString(1, defaultMapper.writeValueAsString(authors))
                            setString(2, title)
                            setString(3, description)
                            setString(4, website)
                            setString(5, defaultMapper.writeValueAsString(tags))

                            setString(6, name)
                            setString(7, version)
                        }
                        .executeUpdate()
                }
            }
        }

        connection.commit()
    }
}
