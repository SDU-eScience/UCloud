package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.FlywayFeature.Feature.SCRIPT_MIGRATE
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.HibernateDatabaseConfig
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.generateDDL

class HibernateFeature(private val config: HibernateFeatureConfiguration) : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(DatabaseConfigurationFeature)

        val configuration = ctx.databaseConfig

        val scriptsToRun = ctx.scriptsToRun
        val validateSchemaOnStartup = !scriptsToRun.contains(SCRIPT_GENERATE_DDL) &&
                !scriptsToRun.contains(SCRIPT_MIGRATE)

        ctx.hibernateDatabase = HibernateSessionFactory.create(
            HibernateDatabaseConfig(
                configuration.driver,
                configuration.jdbcUrl,
                configuration.dialect,
                configuration.username,
                configuration.password,
                configuration.usePool,
                configuration.poolSize,
                configuration.defaultSchema,
                validateSchemaOnStartup = validateSchemaOnStartup,
                recreateSchemaOnStartup = configuration.recreateSchema,
                detectEntitiesInPackages = config.detectFromPackage
            )
        )

        if (ctx.featureOrNull(ScriptFeature) == null) {
            log.info("ScriptFeature is not installed. Cannot add database script handlers")
        } else {
            ctx.optionallyAddScriptHandler(SCRIPT_GENERATE_DDL) {
                println(ctx.hibernateDatabase.generateDDL())

                ScriptHandlerResult.STOP
            }
        }
    }

    companion object Feature : MicroFeatureFactory<HibernateFeature, HibernateFeatureConfiguration>,
        Loggable {
        fun safeSchemaName(service: ServiceDescription): String = service.name.replace('-', '_')

        override val key = MicroAttributeKey<HibernateFeature>("hibernate-feature")
        override fun create(config: HibernateFeatureConfiguration): HibernateFeature = HibernateFeature(config)

        override val log = logger()

        const val SCRIPT_GENERATE_DDL = "generate-ddl"
        internal val SERVICE_KEY =
            MicroAttributeKey<HibernateSessionFactory>("hibernate-session-factory")
    }
}

data class HibernateFeatureConfiguration(val detectFromPackage: List<String> = listOf("dk.sdu.cloud"))

fun Micro.install(feature: HibernateFeature.Feature) {
    install(feature, HibernateFeatureConfiguration())
}

var Micro.hibernateDatabase: HibernateSessionFactory
    get() {
        requireFeature(HibernateFeature)
        return attributes[HibernateFeature.SERVICE_KEY]
    }
    internal set(value) {
        attributes[HibernateFeature.SERVICE_KEY] = value
    }
