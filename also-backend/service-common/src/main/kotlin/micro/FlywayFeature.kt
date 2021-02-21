package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.Loggable
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.flywaydb.core.api.MigrationType
import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.executor.MigrationExecutor
import org.flywaydb.core.api.migration.JavaMigration
import org.flywaydb.core.api.resolver.Context
import org.flywaydb.core.api.resolver.MigrationResolver
import org.flywaydb.core.api.resolver.ResolvedMigration
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.util.*
import kotlin.streams.toList


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

annotation class Schema(val name: String)

fun DatabaseConfig.migrateAll() {
    fun pathInResources(url: URL, internalPath: String): Path {
        val uri = url.toURI()
        return if (uri.scheme == "jar") {
            val fs = FileSystems.newFileSystem(uri, emptyMap<String, Any?>())
            fs.getPath(internalPath)
        } else {
            Paths.get(uri)
        }
    }

    data class SchemaMigrations(val schema: String, val location: Location, val loadedClasses: List<Class<*>>)

    javaClass.classLoader.resources("db/migration")
        .toList()
        .map { migrationUrl ->
            val tempDirectory = Files.createTempDirectory("migration").toFile()

            val potentialClassNames = ArrayList<String>()
            val loadedClasses = ArrayList<Class<*>>()

            Files.newDirectoryStream(pathInResources(migrationUrl, "db/migration")).use { dirStream ->
                dirStream.forEach {
                    if (it.fileName.toString().endsWith(".class") && !it.fileName.toString().contains("$")) {
                        potentialClassNames.add(it.fileName.toString().removeSuffix(".class"))
                    }

                    Files.newInputStream(it).use { ins ->
                        FileOutputStream(File(tempDirectory, it.fileName.toString())).use { fos ->
                            ins.copyTo(fos)
                        }
                    }
                }
            }

            var schema: String? = try {
                URL("$migrationUrl/schema.txt").readText()
            } catch (ex: Throwable) {
                null
            }

            if (potentialClassNames.isNotEmpty()) {
                val newClassLoader = ChildFirstURLClassLoader(
                    arrayOf(URL(migrationUrl.toString().substringBefore("db/migration").removeSuffix("/") + "/")),
                    javaClass.classLoader
                )
                for (className in potentialClassNames) {
                    val s = "db.migration.${className}"
                    val loadedClass = newClassLoader.loadClass(s)
                        ?: error("Could not find schema $migrationUrl (attempted to load class)")
                    val schemaAnnotation = loadedClass.annotations.filterIsInstance<Schema>().singleOrNull()
                        ?: error("Could not find schema $migrationUrl (attempted to locate @Schema annotation)")
                    require(schema == null || schemaAnnotation.name == schema) {
                        "Changing schema name in $migrationUrl. ${schema} vs ${schemaAnnotation.name}"
                    }
                    loadedClasses.add(loadedClass)
                    schema = schemaAnnotation.name
                }
            }

            SchemaMigrations(
                schema ?: error("Could not find schema: $migrationUrl"),
                Location(Location.FILESYSTEM_PREFIX + tempDirectory.absolutePath),
                loadedClasses
            )
        }
        .groupBy { it.schema }
        .forEach { (schema, migrations) ->
            val flyway = Flyway.configure().apply {
                resolvers(object : MigrationResolver {
                    override fun resolveMigrations(context: Context?): MutableCollection<ResolvedMigration> {
                        return migrations.flatMap { it.loadedClasses }
                            .map { runCatching { it.kotlin.constructors.single().call() }.getOrNull() }
                            .filterIsInstance<JavaMigration>()
                            .map { migration ->
                                // Trying really hard to trick flyway into running our code
                                object : ResolvedMigration {
                                    override fun getExecutor(): MigrationExecutor {
                                        return object : MigrationExecutor {
                                            override fun canExecuteInTransaction(): Boolean =
                                                migration.canExecuteInTransaction()

                                            override fun execute(context: org.flywaydb.core.api.executor.Context) {
                                                val ctx = object : org.flywaydb.core.api.migration.Context {
                                                    override fun getConfiguration(): Configuration =
                                                        context.configuration

                                                    override fun getConnection(): Connection = context.connection
                                                }
                                                migration.migrate(ctx)
                                            }
                                        }
                                    }

                                    override fun getVersion(): MigrationVersion {
                                        return migration.version
                                    }

                                    override fun getDescription(): String {
                                        return migration.description
                                    }

                                    override fun getPhysicalLocation(): String {
                                        return ""
                                    }

                                    override fun getType(): MigrationType {
                                        return MigrationType.JDBC
                                    }

                                    override fun getChecksum(): Int {
                                        return 1337
                                    }

                                    override fun getScript(): String {
                                        return "" // I have no idea
                                    }
                                }
                            }
                            .toMutableList()
                    }

                })
                dataSource(jdbcUrl, username, password)
                schemas(schema)
                locations(*migrations.map { it.location }.toTypedArray())
            }.load()

            flyway.migrate()
        }

}

// https://stackoverflow.com/a/6424879 (with modifications)
class ChildFirstURLClassLoader(classpath: Array<URL?>?, parent: ClassLoader?) :
    URLClassLoader(classpath, parent) {
    private val system: ClassLoader?

    @Synchronized
    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        // First, check if the class has already been loaded
        var c = findLoadedClass(name)
        if (c == null) {
            c = try {
                // checking local
                findClass(name)
            } catch (e: ClassNotFoundException) {
                // checking parent
                // This call to loadClass may eventually call findClass again, in case the parent doesn't find anything.
                super.loadClass(name, resolve)
            }
        }
        if (resolve) {
            resolveClass(c)
        }
        return c
    }

    override fun getResource(name: String): URL {
        var url: URL? = null
        if (system != null) {
            url = system.getResource(name)
        }
        if (url == null) {
            url = findResource(name)
            if (url == null) {
                // This call to getResource may eventually call findResource again, in case the parent doesn't find anything.
                url = super.getResource(name)
            }
        }
        return url!!
    }

    @Throws(IOException::class)
    override fun getResources(name: String): Enumeration<URL?> {
        /**
         * Similar to super, but local resources are enumerated before parent resources
         */
        var systemUrls: Enumeration<URL?>? = null
        if (system != null) {
            systemUrls = system.getResources(name)
        }
        val localUrls: Enumeration<URL>? = findResources(name)
        var parentUrls: Enumeration<URL?>? = null
        if (parent != null) {
            parentUrls = parent.getResources(name)
        }
        val urls: MutableList<URL> = ArrayList()
        if (systemUrls != null) {
            while (systemUrls.hasMoreElements()) {
                systemUrls.nextElement()?.let { urls.add(it) }
            }
        }
        if (localUrls != null) {
            while (localUrls.hasMoreElements()) {
                urls.add(localUrls.nextElement())
            }
        }
        if (parentUrls != null) {
            while (parentUrls.hasMoreElements()) {
                parentUrls.nextElement()?.let { urls.add(it) }
            }
        }
        return object : Enumeration<URL?> {
            var iter: Iterator<URL> = urls.iterator()
            override fun hasMoreElements(): Boolean {
                return iter.hasNext()
            }

            override fun nextElement(): URL {
                return iter.next()
            }
        }
    }

    override fun getResourceAsStream(name: String): InputStream? {
        val url = getResource(name)
        try {
            return url?.openStream()!!
        } catch (e: IOException) {
        }
        return null
    }

    init {
        system = ClassLoader.getSystemClassLoader()
    }
}