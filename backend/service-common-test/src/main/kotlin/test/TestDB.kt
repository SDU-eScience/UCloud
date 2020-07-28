package dk.sdu.cloud.service.test

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.DatabaseConfig
import dk.sdu.cloud.micro.safeSchemaName
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway

object TestDB {
    lateinit var db: EmbeddedPostgres
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

    fun dbSessionFactory(defaultSchema: String): AsyncDBSessionFactory {
        return AsyncDBSessionFactory(
            DatabaseConfig(
                jdbcUrl = db.getJdbcUrl("postgres", "postgres"),
                defaultSchema = defaultSchema,
                recreateSchema = false,
                username = "postgres",
                password = "postgres"
            )
        )
    }

    fun getEmbeddedPostgresInfo(): String {
        return db.getJdbcUrl("postgres", "postgres")
    }
}
