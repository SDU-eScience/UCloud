package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.FlywayFeature.Feature.SCRIPT_GENERATE_DDL
import dk.sdu.cloud.micro.FlywayFeature.Feature.SCRIPT_MIGRATE
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
                recreateSchemaOnStartup = configuration.recreateSchema
            )
        )
    }

    companion object Feature : MicroFeatureFactory<HibernateFeature, Unit>,
        Loggable {
        fun safeSchemaName(service: ServiceDescription): String = service.name.replace('-', '_')

        override val key = MicroAttributeKey<HibernateFeature>("hibernate-feature")
        override fun create(config: Unit): HibernateFeature = HibernateFeature()

        override val log = logger()

        internal val SERVICE_KEY =
            MicroAttributeKey<HibernateSessionFactory>("hibernate-session-factory")
    }
}


var Micro.hibernateDatabase: HibernateSessionFactory
    get() {
        requireFeature(HibernateFeature)
        return attributes[HibernateFeature.SERVICE_KEY]
    }
    internal set(value) {
        attributes[HibernateFeature.SERVICE_KEY] = value
    }
