package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.HibernateFeature.Feature.safeSchemaName
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.generateDDL
import org.flywaydb.core.Flyway

class FlywayFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(DatabaseConfigurationFeature)
        val configuration = ctx.databaseConfig

        if (ctx.featureOrNull(ScriptFeature) == null) {
            log.info("ScriptFeature is not installed. Cannot add database script handlers")
        } else {
            ctx.optionallyAddScriptHandler(SCRIPT_MIGRATE) {
                val username = configuration.username ?: ""
                val password = configuration.password ?: ""
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
        const val SCRIPT_MIGRATE = "migrate-db"
    }
}
