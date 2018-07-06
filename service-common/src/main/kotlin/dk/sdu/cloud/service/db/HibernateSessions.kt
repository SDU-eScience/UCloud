package dk.sdu.cloud.service.db

import eu.infomas.annotation.AnnotationDetector
import org.hibernate.SessionFactory
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.slf4j.LoggerFactory
import javax.persistence.Entity

typealias HibernateSession = org.hibernate.Session

class HibernateSessionFactory(
    @PublishedApi
    internal val factory: SessionFactory,

    @PublishedApi
    internal val metadata: Metadata,

    val autoDetectedEntities: List<Class<*>> = emptyList()
) : DBSessionFactory<HibernateSession> {
    override fun <R> withSession(closure: HibernateSession.() -> R): R {
        val session = factory.openSession()
        return session.use(closure)
    }

    override fun <R> withTransaction(
        session: HibernateSession,
        autoCommit: Boolean,
        closure: (HibernateSession) -> R
    ): R {
        with(session) {
            beginTransaction()
            val result = closure(this)
            if (autoCommit) transaction.commit()
            return result
        }
    }

    override fun commit(session: HibernateSession) {
        session.transaction.commit()
    }

    override fun close() {
        factory.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(HibernateSessionFactory::class.java)

        fun create(config: HibernateDatabaseConfig? = null): HibernateSessionFactory {
            val registry = StandardServiceRegistryBuilder().apply {
                if (config?.skipXml != true) {
                    configure()
                }

                if (config == null) return@apply

                with(config) {
                    if (driver != null) applySetting("hibernate.connection.driver_class", driver)
                    if (jdbcUrl != null) applySetting("hibernate.connection.url", jdbcUrl)
                    if (username != null) applySetting("hibernate.connection.username", username)
                    applySetting("hibernate.connection.password", password ?: "")
                    if (poolSize != null) applySetting("hibernate.connection.pool_size", poolSize.toString())
                    if (dialect != null) applySetting("hibernate.dialect", dialect)
                    if (showSQLInStdout) applySetting("hibernate.show_sql", true.toString())
                    if (recreateSchemaOnStartup) applySetting("hibernate.hbm2ddl.auto", "create")
                    else if (validateSchemaOnStartup) applySetting("hibernate.hbm2ddl.auto", "validate")
                    applySetting("hibernate.default_schema", config.defaultSchema)
                    applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                    applySetting(
                        "hibernate.connection.provider_class",
                        "org.hibernate.hikaricp.internal.HikariCPConnectionProvider"
                    )

                    applySetting(
                        "hibernate.physical_naming_strategy",
                        SnakeCasePhysicalNamingStrategy::class.qualifiedName
                    )

                    if (skipXml && !autoDetectEntities) {
                        log.warn("Skipping XML configuration but also not auto detecting entities")
                    }
                }
            }.build()

            val entities = if (config?.autoDetectEntities == true) {
                detectEntities(*config.detectEntitiesInPackages.toTypedArray())
            } else {
                emptyList()
            }

            return (try {
                MetadataSources(registry).apply {
                    entities.forEach { addAnnotatedClass(it) }
                }.buildMetadata()
            } catch (ex: Exception) {
                StandardServiceRegistryBuilder.destroy(registry)
                throw ex
            }).let { HibernateSessionFactory(it.buildSessionFactory(), it, entities) }
        }
    }
}

data class HibernateDatabaseConfig(
    val driver: String?,
    val jdbcUrl: String?,
    val dialect: String?,
    val username: String?,
    val password: String?,
    val poolSize: Int? = 10,
    val defaultSchema: String = "public",
    val skipXml: Boolean = true,
    val showSQLInStdout: Boolean = false,
    val recreateSchemaOnStartup: Boolean = false,
    val validateSchemaOnStartup: Boolean = false,
    val autoDetectEntities: Boolean = true,
    val detectEntitiesInPackages: List<String> = listOf("dk.sdu.cloud")
)

const val H2_DRIVER = "org.h2.Driver"
const val H2_TEST_JDBC_URL = "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE"
const val H2_DIALECT = "org.hibernate.dialect.H2Dialect"

val H2_TEST_CONFIG = HibernateDatabaseConfig(
    driver = H2_DRIVER,
    jdbcUrl = H2_TEST_JDBC_URL,
    dialect = H2_DIALECT,
    username = "sa",
    password = "",
    poolSize = 1,
    recreateSchemaOnStartup = true
)

const val POSTGRES_DRIVER = "org.postgresql.Driver"
const val POSTGRES_9_5_DIALECT = "org.hibernate.dialect.PostgreSQL95Dialect"

fun postgresJdbcUrl(host: String, database: String, port: Int? = null): String {
    return StringBuilder().apply {
        append("jdbc:postgresql://")
        append(host)
        if (port != null) {
            append(':')
            append(port)
        }
        append('/')
        append(database)
    }.toString()
}

private fun detectEntities(vararg where: String): List<Class<*>> {
    val entities = mutableListOf<Class<*>>()
    AnnotationDetector(object : AnnotationDetector.TypeReporter {
        override fun reportTypeAnnotation(annotation: Class<out Annotation>?, className: String?) {
            entities.add(Class.forName(className))
        }

        override fun annotations(): Array<out Class<out Annotation>> = arrayOf(Entity::class.java)
    }).detect(*where)
    return entities
}