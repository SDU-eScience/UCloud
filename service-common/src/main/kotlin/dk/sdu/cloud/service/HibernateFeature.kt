package dk.sdu.cloud.service

import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.service.db.*
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
            Profile.TEST_H2 -> {
                ctx.jdbcUrl = H2_TEST_JDBC_URL
                ctx.hibernateDatabase =
                        HibernateSessionFactory.create(H2_TEST_CONFIG.copy(showSQLInStdout = configuration.logSql))
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
                        defaultSchema = ctx.serviceDescription.name,
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
                val flyway = Flyway()
                val username = configuration.credentials?.username ?: ""
                val password = configuration.credentials?.password ?: ""
                val jdbcUrl = ctx.jdbcUrl
                flyway.setDataSource(jdbcUrl, username, password)
                flyway.setSchemas(serviceDescription.name)
                flyway.migrate()

                ScriptHandlerResult.STOP
            }
        }
    }

    companion object Feature : MicroFeatureFactory<HibernateFeature, Unit>, Loggable {
        override val key = MicroAttributeKey<HibernateFeature>("hibernate-feature")
        override fun create(config: Unit): HibernateFeature = HibernateFeature()

        override val log = logger()

        internal val SERVICE_KEY = MicroAttributeKey<HibernateSessionFactory>("hibernate-session-factory")
        internal val JDBC_KEY = MicroAttributeKey<String>("hibernate-jdbc-url")

        // Script args
        const val SCRIPT_GENERATE_DDL = "generate-ddl"
        const val SCRIPT_MIGRATE = "migrate-db"

        // Config chunks
        val CONFIG_PATH = arrayOf("hibernate", "database")

        enum class Profile {
            TEST_H2,
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