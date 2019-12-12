package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.HibernateFeature.Feature.safeSchemaName
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.generateDDL
import org.flywaydb.core.Flyway

class FlywayFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        val configuration = ctx.configuration.requestChunkAtOrNull(*HibernateFeature.CONFIG_PATH) ?: run {
            log.warn(
                "No database configuration provided at ${HibernateFeature.CONFIG_PATH.toList()}. " +
                        "Using default test (non-persistent) database."
            )

            HibernateFeature.Feature.Config()
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

    companion object Feature : MicroFeatureFactory<FlywayFeature, Unit>, Loggable {
        override val log = logger()
        override val key: MicroAttributeKey<FlywayFeature> = MicroAttributeKey("flyway-feature")
        override fun create(config: Unit): FlywayFeature = FlywayFeature()

        // Script args
        const val SCRIPT_GENERATE_DDL = "generate-ddl"
        const val SCRIPT_MIGRATE = "migrate-db"
    }
}
