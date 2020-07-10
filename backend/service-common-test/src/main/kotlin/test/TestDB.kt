import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.DatabaseConfig
import dk.sdu.cloud.micro.HibernateFeature.Feature.safeSchemaName
import dk.sdu.cloud.service.db.POSTGRES_9_5_DIALECT
import dk.sdu.cloud.service.db.POSTGRES_DRIVER
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
                    driver = POSTGRES_DRIVER,
                    dialect = POSTGRES_9_5_DIALECT,
                    username = "postgres",
                    password = "postgres"
                )
            ),
            db
        )
    }

    fun getEmbeddedPostgresInfo(): String {
        return db.getJdbcUrl("postgres", "postgres")
    }
}
