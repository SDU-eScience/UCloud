package dk.sdu.cloud.sql

class MigrationScript(
    val id: String,
    val execute: suspend (conn: DBContext.Connection) -> Unit,
)

class MigrationHandler(private val connection: DBContext) {
    private val scripts = ArrayList<MigrationScript>()

    fun addScript(id: String, execute: (conn: DBContext.Connection) -> Unit) {
        addScript(MigrationScript(id, execute))
    }

    fun addScript(script: MigrationScript) {
        with(script) {
            check(scripts.all { it.id != id }) { "A migration with the ID '$id' already exists" }
            scripts.add(script)
        }
    }

    suspend fun migrate() {
        val missingMigrations = ArrayList<String>()
        connection.withSession { connection ->
            try {
                //language=postgresql
                connection.prepareStatement(
                    """
                        create table if not exists migrations(
                            id text primary key
                        );
                    """
                ).use { it.invokeAndDiscard() }

                connection.prepareStatement(
                    //language=postgresql
                    """
                        create table if not exists completed_migrations(
                            id text primary key references migrations,
                            completed_at timestamp
                        );
                    """
                ).use { it.invokeAndDiscard() }
            } catch (ex: Throwable) {
                throw MigrationException("Failed to initialize migration tables", ex)
            }

            try {
                scripts.forEach { script ->
                    connection.prepareStatement(
                        //language=postgresql
                        """
                            insert into migrations(id) values (:id) on conflict (id) do nothing
                        """
                    ).invokeAndDiscard {
                        bindString("id", script.id)
                    }
                }
            } catch (ex: Throwable) {
                throw MigrationException("Failed to register migrations", ex)
            }
        }

        connection.withSession { connection ->
            try {
                //language=SQLite
                connection.prepareStatement(
                    """
                        select m.id
                        from migrations m left join completed_migrations cm on m.id = cm.id
                        where cm.id is null
                    """
                ).use {
                    it.invoke { row ->
                        missingMigrations.add(row.getString(0)!!)
                    }
                }
            } catch (ex: Throwable) {
                throw MigrationException("Failed to fetch missing migrations", ex)
            }
        }

        val groupedScripts = scripts.associateBy { it.id }
        for (migrationId in missingMigrations) {
            val migration = groupedScripts[migrationId] ?: continue
            try {
                connection.withSession { connection ->
                    migration.execute(connection)

                    // NOTE(Dan): This needs to be prepared everytime because of schema changes made by the migrations.
                    connection.prepareStatement(
                        //language=postgresql
                        """
                            insert into completed_migrations (id, completed_at) values (:id, now())
                        """
                    ).use {
                        it.invokeAndDiscard {
                            bindString("id", migrationId)
                        }
                    }
                }
            } catch (ex: Throwable) {
                throw MigrationException("Failed to run migration: $migrationId", ex)
            }
        }
    }
}

class MigrationException(message: String, cause: Throwable?) : RuntimeException(message, cause)
