package dk.sdu.cloud.service.test

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.DatabaseConfig
import dk.sdu.cloud.micro.DatabaseConfigurationFeature
import dk.sdu.cloud.micro.safeSchemaName
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway

object TestDB {
    private lateinit var db: EmbeddedPostgres
    fun from(serviceDescription: ServiceDescription): Pair<AsyncDBSessionFactory, EmbeddedPostgres> {
        db = EmbeddedPostgres.start()
        val flyway = Flyway.configure().apply {
            dataSource(db.postgresDatabase)
            schemas(safeSchemaName(serviceDescription))
        }.load()
        flyway.migrate()
        return Pair<AsyncDBSessionFactory, EmbeddedPostgres>(
            AsyncDBSessionFactory(
                DatabaseConfig(
                    jdbcUrl = db.getJdbcUrl("postgres", "postgres"),
                    defaultSchema = safeSchemaName(serviceDescription),
                    recreateSchema = false,
                    username = "postgres",
                    password = "postgres"
                )
            ),
            db
        )
    }

    fun initializeWithoutService() {
        db = EmbeddedPostgres.start()
    }

    fun getBaseConfig(): DatabaseConfig {
        return DatabaseConfig(
            jdbcUrl = db.getJdbcUrl("postgres", "postgres"),
            defaultSchema = "public",
            recreateSchema = false,
            username = "postgres",
            password = "postgres"
        )
    }

    fun getConfigForFeature(): DatabaseConfigurationFeature.Feature.Config {
        return DatabaseConfigurationFeature.Feature.Config(
            hostname = "localhost",
            port = db.port,
            credentials = DatabaseConfigurationFeature.Feature.Credentials("postgres", "postgres"),
            database = "postgres"
        )
    }

    fun close() {
        if (this::db.isInitialized) {
            db.close()
        }
    }

    fun dbSessionFactory(defaultSchema: String): AsyncDBSessionFactory {
        return AsyncDBSessionFactory(
            getBaseConfig().copy(defaultSchema = defaultSchema)
        )
    }

    fun getEmbeddedPostgresInfo(): String {
        return db.getJdbcUrl("postgres", "postgres")
    }
}
