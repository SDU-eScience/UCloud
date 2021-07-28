package dk.sdu.cloud.service.test

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.DatabaseConfig
import dk.sdu.cloud.micro.DatabaseConfigurationFeature
import dk.sdu.cloud.micro.postgresJdbcUrl
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
        return Pair(dbSessionFactory(safeSchemaName(serviceDescription)), db)
    }

    private var baseConfigFromEnv: DatabaseConfig? = null
    private var dbConfigForFeature: DatabaseConfigurationFeature.Feature.Config? = null

    const val ENV = "INTEGRATION_TEST_POSTGRES_"
    const val CONFIG_HOSTNAME = ENV + "HOSTNAME"
    const val CONFIG_USERNAME = ENV + "USERNAME"
    const val CONFIG_PASSWORD = ENV + "PASSWORD"
    const val CONFIG_PORT = ENV + "PORT"
    const val CONFIG_DATABASE = ENV + "DATABASE"

    fun initializeWithoutService() {
        val hostname = System.getenv(CONFIG_HOSTNAME)
        val username = System.getenv(CONFIG_USERNAME)
        val password = System.getenv(CONFIG_PASSWORD)
        val port = System.getenv(CONFIG_PORT)
        val database = System.getenv(CONFIG_DATABASE)
        if (hostname != null && username != null && password != null) {
            val portToUse = port?.toIntOrNull() ?: 5432
            val databaseToUse = database ?: "postgres"

            baseConfigFromEnv = DatabaseConfig(
                jdbcUrl = postgresJdbcUrl(hostname, databaseToUse, portToUse),
                username = username,
                password = password,
                defaultSchema = "public",
                recreateSchema = true
            )

            dbConfigForFeature = DatabaseConfigurationFeature.Feature.Config(
                hostname = hostname,
                database = databaseToUse,
                credentials = DatabaseConfigurationFeature.Feature.Credentials(username, password),
                port = portToUse
            )
        } else {
            db = EmbeddedPostgres.start()
            dbConfigForFeature = DatabaseConfigurationFeature.Feature.Config(
                hostname = "localhost",
                port = db.port,
                credentials = DatabaseConfigurationFeature.Feature.Credentials("postgres", "postgres"),
                database = "postgres"
            )
        }
    }

    fun close() {
        if (this::db.isInitialized) {
            db.close()
        }
    }

    fun getFeatureConfig(): DatabaseConfigurationFeature.Feature.Config {
        return dbConfigForFeature ?: error("Not yet initialized")
    }

    fun getBaseConfig(): DatabaseConfig {
        return baseConfigFromEnv ?: DatabaseConfig(
            jdbcUrl = db.getJdbcUrl("postgres", "postgres"),
            defaultSchema = "public",
            recreateSchema = false,
            username = "postgres",
            password = "postgres"
        )
    }

    fun dbSessionFactory(defaultSchema: String): AsyncDBSessionFactory {
        return AsyncDBSessionFactory(
            getBaseConfig().copy(defaultSchema = defaultSchema)
        )
    }
}
