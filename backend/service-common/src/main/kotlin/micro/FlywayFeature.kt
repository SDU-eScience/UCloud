package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.Loggable
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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

    javaClass.classLoader.resources("db/migration").forEach outer@{ migrationUrl ->
        val tempDirectory = Files.createTempDirectory("migration").toFile()

        Files.newDirectoryStream(pathInResources(migrationUrl, "db/migration")).use { dirStream ->
            dirStream.forEach {
                if (it.fileName.toString().endsWith(".class")) return@outer
                Files.newInputStream(it).use { ins ->
                    FileOutputStream(File(tempDirectory, it.fileName.toString())).use { fos ->
                        ins.copyTo(fos)
                    }
                }
            }
        }

        val schema = try {
            URL("$migrationUrl/schema.txt").readText()
        } catch (ex: Throwable) {
            throw RuntimeException(
                "Could not find 'schema.txt' in $migrationUrl ${URL("$migrationUrl/schema.txt")}",
                ex
            )
        }

        val flyway = Flyway.configure().apply {
            dataSource(jdbcUrl, username, password)
            schemas(schema)
            locations(Location(Location.FILESYSTEM_PREFIX + tempDirectory.absolutePath))
        }.load()

        flyway.migrate()
    }
}
