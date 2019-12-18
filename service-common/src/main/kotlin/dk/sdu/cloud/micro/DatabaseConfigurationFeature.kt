package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.HibernateFeature.Feature.safeSchemaName
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.H2_DIALECT
import dk.sdu.cloud.service.db.H2_DRIVER
import dk.sdu.cloud.service.db.H2_TEST_JDBC_URL
import dk.sdu.cloud.service.db.POSTGRES_9_5_DIALECT
import dk.sdu.cloud.service.db.POSTGRES_DRIVER
import dk.sdu.cloud.service.db.postgresJdbcUrl
import dk.sdu.cloud.service.findValidHostname

class DatabaseConfigurationFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(ConfigurationFeature)

        fun invalidConfig(why: String): Nothing =
            throw IllegalStateException("$why. Please provide it in configuration at ${CONFIG_PATH.toList()}")

        val configuration =
            ctx.configuration.requestChunkAtOrNull(*CONFIG_PATH)
                ?: ctx.configuration.requestChunkAtOrNull(*OLD_CONFIG_PATH) ?: run {
                    log.warn(
                        "No database configuration provided at ${CONFIG_PATH.toList()}. " +
                                "Will fall back to default test (non-persistent) database."
                    )

                    Config()
                }

        log.info("Using ${configuration.profile} database configuration profile.")
        when (configuration.profile) {
            Profile.PERSISTENT_H2 -> {
                ctx.jdbcUrl = "jdbc:h2:~/${configuration.database ?: "h2-persistent"}.db"
                ctx.databaseConfig =
                    DatabaseConfig(
                        driver = H2_DRIVER,
                        dialect = H2_DIALECT,
                        jdbcUrl = ctx.jdbcUrl,
                        username = null,
                        password = null,
                        recreateSchema = false,
                        defaultSchema = safeSchemaName(ctx.serviceDescription)
                    )
            }

            Profile.TEST_H2 -> {
                ctx.jdbcUrl = H2_TEST_JDBC_URL
                ctx.databaseConfig = DatabaseConfig(
                    driver = H2_DRIVER,
                    dialect = H2_DIALECT,
                    jdbcUrl = ctx.jdbcUrl,
                    username = null,
                    password = null,
                    recreateSchema = true,
                    defaultSchema = "public"
                )
            }

            Profile.PERSISTENT_POSTGRES -> {
                val credentials = configuration.credentials
                    ?: invalidConfig("Cannot connect to postgres without credentials")

                val hostname = configuration.hostname ?: run {
                    log.info(
                        "No hostname given in configuration. Looking for valid hostname: " +
                                "$postgresExpectedHostnames"
                    )

                    findValidHostname(postgresExpectedHostnames)
                } ?: throw IllegalStateException("Could not find a valid host")

                val database = configuration.database ?: "postgres"
                val port = configuration.port
                val jdbcUrl = postgresJdbcUrl(hostname, database, port)

                val driver = configuration.driver ?: POSTGRES_DRIVER
                val dialect = configuration.dialect ?: POSTGRES_9_5_DIALECT

                ctx.jdbcUrl = jdbcUrl
                ctx.databaseConfig = DatabaseConfig(
                    driver,
                    jdbcUrl,
                    dialect,
                    credentials.username,
                    credentials.password,
                    safeSchemaName(ctx.serviceDescription),
                    recreateSchema = false
                )
            }
        }
    }

    companion object Feature : Loggable, MicroFeatureFactory<DatabaseConfigurationFeature, Unit> {
        override val log = logger()

        internal val JDBC_KEY = MicroAttributeKey<String>("jdbc-url")
        internal val CONFIG_KEY = MicroAttributeKey<DatabaseConfig>("db-config")

        // Config chunks
        val OLD_CONFIG_PATH = arrayOf("hibernate", "database")
        val CONFIG_PATH = arrayOf("database")

        enum class Profile {
            TEST_H2,
            PERSISTENT_H2,
            PERSISTENT_POSTGRES
        }

        data class Credentials(val username: String, val password: String)

        data class Config(
            val profile: Profile = Profile.TEST_H2,
            val hostname: String? = null,
            val credentials: Credentials? = null,
            val driver: String? = null,
            val dialect: String? = null,
            val database: String? = null,
            val port: Int? = null,
            val logSql: Boolean = false
        )

        // Postgres profile
        private val postgresExpectedHostnames = listOf(
            "postgres",
            "localhost"
        )
        override val key: MicroAttributeKey<DatabaseConfigurationFeature> = MicroAttributeKey("database-config")

        override fun create(config: Unit): DatabaseConfigurationFeature = DatabaseConfigurationFeature()
    }
}

var Micro.jdbcUrl: String
    get() {
        requireFeature(DatabaseConfigurationFeature)
        return attributes[DatabaseConfigurationFeature.JDBC_KEY]
    }
    internal set(value) {
        attributes[DatabaseConfigurationFeature.JDBC_KEY] = value
    }

var Micro.databaseConfig: DatabaseConfig
    get() {
        requireFeature(DatabaseConfigurationFeature)
        return attributes[DatabaseConfigurationFeature.CONFIG_KEY]
    }
    internal set(value) {
        attributes[DatabaseConfigurationFeature.CONFIG_KEY] = value
    }

data class DatabaseConfig(
    val driver: String?,
    val jdbcUrl: String?,
    val dialect: String?,
    val username: String?,
    val password: String?,
    val defaultSchema: String,
    val recreateSchema: Boolean,
    val usePool: Boolean = true,
    val poolSize: Int? = 50
)
