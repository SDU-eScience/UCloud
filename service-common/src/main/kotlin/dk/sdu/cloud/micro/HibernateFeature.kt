package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.H2_TEST_JDBC_URL
import dk.sdu.cloud.service.db.HibernateDatabaseConfig
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.POSTGRES_9_5_DIALECT
import dk.sdu.cloud.service.db.POSTGRES_DRIVER
import dk.sdu.cloud.service.db.generateDDL
import dk.sdu.cloud.service.db.postgresJdbcUrl
import dk.sdu.cloud.service.findValidHostname
import org.flywaydb.core.Flyway

class HibernateFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(ConfigurationFeature)

        fun invalidConfig(why: String): Nothing =
            throw IllegalStateException("$why. Please provide it in configuration at ${CONFIG_PATH.toList()}")

        val configuration = ctx.configuration.requestChunkAtOrNull(*CONFIG_PATH) ?: run {
            log.warn(
                "No database configuration provided at ${CONFIG_PATH.toList()}. " +
                        "Using default test (non-persistent) database."
            )

            Config()
        }

        log.info("Using ${configuration.profile} database configuration profile.")
        when (configuration.profile) {
            Feature.Profile.PERSISTENT_H2 -> {
                ctx.jdbcUrl = "jdbc:h2:~/${configuration.database ?: "h2-persistent"}.db"
                ctx.hibernateDatabase =
                    HibernateSessionFactory.create(
                        H2_TEST_CONFIG.copy(
                            jdbcUrl = ctx.jdbcUrl,
                            showSQLInStdout = configuration.logSql,
                            username = null,
                            password = null,
                            recreateSchemaOnStartup = false,
                            defaultSchema = safeSchemaName(ctx.serviceDescription)
                        )
                    )
            }

            Feature.Profile.TEST_H2 -> {
                ctx.jdbcUrl = H2_TEST_JDBC_URL
                ctx.hibernateDatabase =
                    HibernateSessionFactory.create(H2_TEST_CONFIG.copy(showSQLInStdout = configuration.logSql))
            }

            Feature.Profile.PERSISTENT_POSTGRES -> {
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

                val driver = configuration.driver ?: POSTGRES_DRIVER
                val dialect = configuration.dialect ?: POSTGRES_9_5_DIALECT

                val scriptsToRun = ctx.scriptsToRun
                val jdbcUrl = postgresJdbcUrl(hostname, database, port)
                val db = HibernateSessionFactory.create(
                    HibernateDatabaseConfig(
                        driver,
                        jdbcUrl,
                        dialect,
                        credentials.username,
                        credentials.password,
                        defaultSchema = safeSchemaName(ctx.serviceDescription),
                        validateSchemaOnStartup = !scriptsToRun.contains(SCRIPT_GENERATE_DDL) &&
                                !scriptsToRun.contains(SCRIPT_MIGRATE),
                        showSQLInStdout = configuration.logSql
                    )
                )

                ctx.jdbcUrl = jdbcUrl
                ctx.hibernateDatabase = db
            }
        }

        if (ctx.featureOrNull(ScriptFeature) == null) {
            log.info("ScriptFeature is not installed. Cannot add database script handlers")
        } else {
            ctx.optionallyAddScriptHandler(SCRIPT_GENERATE_DDL) {
                println(ctx.hibernateDatabase.generateDDL())

                ScriptHandlerResult.STOP
            }

            ctx.optionallyAddScriptHandler(SCRIPT_MIGRATE) {
                val username = configuration.credentials?.username ?: ""
                val password = configuration.credentials?.password ?: ""
                val jdbcUrl = ctx.jdbcUrl

                val flyway = Flyway.configure().apply {
                    dataSource(jdbcUrl, username, password)
                    schemas(safeSchemaName(serviceDescription))
                }.load()

                flyway.migrate()
                ScriptHandlerResult.STOP
            }
        }
    }

    private fun safeSchemaName(service: ServiceDescription): String = service.name.replace('-', '_')

    companion object Feature : MicroFeatureFactory<HibernateFeature, Unit>,
        Loggable {
        override val key = MicroAttributeKey<HibernateFeature>("hibernate-feature")
        override fun create(config: Unit): HibernateFeature = HibernateFeature()

        override val log = logger()

        internal val SERVICE_KEY =
            MicroAttributeKey<HibernateSessionFactory>("hibernate-session-factory")
        internal val JDBC_KEY = MicroAttributeKey<String>("hibernate-jdbc-url")

        // Script args
        const val SCRIPT_GENERATE_DDL = "generate-ddl"
        const val SCRIPT_MIGRATE = "migrate-db"

        // Config chunks
        val CONFIG_PATH = arrayOf("hibernate", "database")

        enum class Profile {
            TEST_H2,
            PERSISTENT_H2,
            PERSISTENT_POSTGRES
        }

        data class Credentials(val username: String, val password: String)

        data class Config(
            val profile: Profile = Feature.Profile.TEST_H2,
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
    }
}

var Micro.jdbcUrl: String
    get() {
        requireFeature(HibernateFeature)
        return attributes[HibernateFeature.JDBC_KEY]
    }
    internal set(value) {
        attributes[HibernateFeature.JDBC_KEY] = value
    }

var Micro.hibernateDatabase: HibernateSessionFactory
    get() {
        requireFeature(HibernateFeature)
        return attributes[HibernateFeature.SERVICE_KEY]
    }
    internal set(value) {
        attributes[HibernateFeature.SERVICE_KEY] = value
    }
