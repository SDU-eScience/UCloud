package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.Time
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.appender.MemoryMappedFileAppender
import org.apache.logging.log4j.core.config.AppenderRef
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.core.config.Order
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.filter.ThresholdFilter
import org.apache.logging.log4j.core.layout.PatternLayout
import java.io.File
import java.net.URI

@Order(Int.MAX_VALUE)
@Plugin(name = "Log4j2ConfigFactory", category = ConfigurationFactory.CATEGORY)
object Log4j2ConfigFactory : ConfigurationFactory() {
    private const val fileLog = "file"
    private const val stdoutLog = "stdout"
    private lateinit var loggerContext: LoggerContext

    override fun getConfiguration(loggerContext: LoggerContext, name: String, configLocation: URI?): Configuration {
        return buildConfiguration(loggerContext, name)
    }

    override fun getConfiguration(
        loggerContext: LoggerContext,
        name: String,
        configLocation: URI?,
        loader: ClassLoader?
    ): Configuration {
        return buildConfiguration(loggerContext, name)
    }

    override fun getConfiguration(loggerContext: LoggerContext, source: ConfigurationSource): Configuration {
        return buildConfiguration(loggerContext, source.toString())
    }

    // Kotlin is not wild about this recursive generic pattern that Log4j is using. As a result, we need to create a
    // more concrete type to make the type-system happy.
    private class MemoryMappedFileAppenderBuilderKtFix : MemoryMappedFileAppender.Builder<MemoryMappedFileAppenderBuilderKtFix>()

    private fun buildConfiguration(loggerContext: LoggerContext, name: String): Configuration {
        this.loggerContext = loggerContext

        return newConfigurationBuilder().apply {
            val defaultLevel = Level.INFO

            setConfigurationName(name)

            val pattern = "%highlight{" +
                "%d{HH:mm:ss.SSS} " +
                //"[%t] " +
                "(%X{request-id}) " +
                "%level{TRACE=T, DEBUG=D, INFO=I, WARN=WARNING, ERROR=ERROR, FATAL=FATAL} " +
                "%c{-2} - " +
                "%msg%n" +
                "}"

            add(
                newAppender(stdoutLog, "CONSOLE")
                    .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                    .add(newFilter("ThresholdFilter", "ACCEPT", "DENY").addAttribute("level", "WARN"))
                    .add(newLayout("PatternLayout").addAttribute("pattern", pattern))
            )

            var logLocation: String? = null
            val potentialLogFileLocations = listOf("./logs", "/var/log/ucloud", "/tmp/")
            for (loc in potentialLogFileLocations) {
                val dir = File(loc)
                if (!dir.exists()) {
                    if (!dir.mkdirs()) continue
                }

                try {
                    val testFile = File(dir, "log_test_${Time.now()}.txt").also { it.writeText("Test") }
                    testFile.delete()

                    logLocation = loc
                } catch (ex: Throwable) {
                    continue
                }
            }

            var rootLogger = newRootLogger(defaultLevel)
            if (logLocation != null) {
                add(
                    newAppender(fileLog, "MemoryMappedFile")
                        .addAttribute("fileName", File(logLocation, "debug.log").absolutePath)
                        .add(newLayout("PatternLayout").addAttribute("pattern", pattern))
                )
                rootLogger = rootLogger.add(newAppenderRef(fileLog))
            }
            rootLogger = rootLogger.add(newAppenderRef(stdoutLog))
            add(rootLogger)

            configureLogLevelForPackage("io.lettuce", Level.INFO)
            configureLogLevelForPackage("io.netty", Level.INFO)
            configureLogLevelForPackage("dk.sdu.cloud.service.EventProducer", Level.INFO)
            configureLogLevelForPackage("org.hibernate", Level.INFO)
            configureLogLevelForPackage("com.zaxxer.hikari", Level.INFO)
            configureLogLevelForPackage("io.mockk.impl", Level.INFO)
            configureLogLevelForPackage("com.github.jasync.sql.db.postgresql.codec.PostgreSQLConnectionHandler", Level.WARN)
            configureLogLevelForPackage("io.lettuce.core.EpollProvider", Level.WARN)
            configureLogLevelForPackage("io.lettuce.core.KqueueProvider", Level.WARN)
            configureLogLevelForPackage("com.github.jasync.sql.db.util", Level.WARN)
            configureLogLevelForPackage("com.github.jasync.sql.db.pool.ActorBasedObjectPool", Level.WARN)
            configureLogLevelForPackage("com.github.jasync.sql.db.pool.ConnectionPool", Level.INFO)
            configureLogLevelForPackage("com.github.jasync.sql.db.postgresql", Level.INFO)
            configureLogLevelForPackage("com.github.jasync.sql.db.postgresql.PostgreSQLConnection", Level.FATAL)
            configureLogLevelForPackage("org.apache.xml.security.Init", Level.INFO)
            configureLogLevelForPackage("reactor.util.Loggers\$LoggerFactory", Level.INFO)
            configureLogLevelForPackage("reactor.core.publisher.UnsafeSupport", Level.INFO)
            configureLogLevelForPackage("ktor.application", Level.WARN)
            configureLogLevelForPackage("org.flywaydb.core.internal", Level.ERROR)
            configureLogLevelForPackage("org.flywaydb.core.Flyway", Level.DEBUG)
            configureLogLevelForPackage("org.asynchttpclient", Level.INFO)

            val ctx = ctx
            if (ctx != null && ctx.developmentModeEnabled) {
                configureLogLevelForPackage("dk.sdu.cloud.calls.client.OutgoingHttpRequestInterceptor", Level.INFO)
            }
        }.build().also {
            initializeFn?.invoke()
            initializeFn = null
        }
    }

    private fun ConfigurationBuilder<*>.configureLogLevelForPackage(packageName: String, level: Level) {
        add(
            newLogger(packageName, level)
                .add(newAppenderRef(stdoutLog))
                .add(newAppenderRef(fileLog))
                .addAttribute("additivity", true)
        )
    }

    private fun reconfigureLogger(packageName: String, level: Level) {
        loggerContext.configuration.removeLogger(packageName)
        loggerContext.configuration.addLogger(
            packageName,
            LoggerConfig.createLogger(
                true,
                level,
                packageName,
                null,
                arrayOf(
                    AppenderRef.createAppenderRef(stdoutLog, null, null),
                    AppenderRef.createAppenderRef(fileLog, null, null),
                ),
                null,
                loggerContext.configuration,
                null
            )
        )
    }

    private var ctx: Micro? = null
    private var initializeFn: (() -> Unit)? = null

    fun initialize(ctx: Micro) {
        this.ctx = ctx
        if (!this::loggerContext.isInitialized) {
            ConfigurationFactory.setConfigurationFactory(Log4j2ConfigFactory)
        }
        initializeFn = {
            if (ctx.developmentModeEnabled || ctx.commandLineArguments.contains("--debug")) {
                loggerContext.configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).level = Level.DEBUG
            }

            loggerContext.updateLoggers()
        }

        if (this::loggerContext.isInitialized) {
            initializeFn!!()
            initializeFn = null
        }
    }

    fun configureLevels(levels: Map<String, Level>) {
        levels.forEach { (packageName, level) ->
            reconfigureLogger(packageName, level)
        }

        loggerContext.updateLoggers()
    }

    override fun getSupportedTypes(): Array<String> = arrayOf("*")
}

class LogFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        Log4j2ConfigFactory.initialize(ctx)
    }

    fun configureLevels(levels: Map<String, Level>) {
        Log4j2ConfigFactory.configureLevels(levels)
    }

    companion object Feature : MicroFeatureFactory<LogFeature, Unit> {
        override val key: MicroAttributeKey<LogFeature> = MicroAttributeKey("log-feature")
        override fun create(config: Unit): LogFeature = LogFeature()
    }
}
