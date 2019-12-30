package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.config.AppenderRef
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.core.config.Order
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder
import org.apache.logging.log4j.core.config.plugins.Plugin
import java.net.URI

@Order(Int.MAX_VALUE)
@Plugin(name = "Log4j2ConfigFactory", category = ConfigurationFactory.CATEGORY)
internal object Log4j2ConfigFactory : ConfigurationFactory() {
    private const val appenderRef = "stdout"
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

    private fun buildConfiguration(loggerContext: LoggerContext, name: String): Configuration {
        this.loggerContext = loggerContext

        return newConfigurationBuilder().apply {
            val defaultLevel = Level.INFO

            setConfigurationName(name)

            add(
                newAppender(appenderRef, "CONSOLE")
                    .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                    .add(
                        newLayout("PatternLayout")
                            .addAttribute(
                                "pattern",
                                "%highlight{" +
                                        "%d{HH:mm:ss.SSS} " +
                                        "[%t] " +
                                        "(%X{request-id}) " +
                                        "%level{TRACE=T, DEBUG=D, INFO=I, WARN=WARNING, ERROR=ERROR, FATAL=FATAL} " +
                                        "%c{-2} - " +
                                        "%msg%n" +
                                        "}"
                            )
                    )
            )

            add(
                newRootLogger(defaultLevel).add(
                    newAppenderRef(appenderRef)
                )
            )

            configureLogLevelForPackage("io.lettuce", Level.INFO)
            configureLogLevelForPackage("io.netty", Level.INFO)
            configureLogLevelForPackage("dk.sdu.cloud.service.EventProducer", Level.INFO)
            configureLogLevelForPackage("org.hibernate", Level.INFO)
            configureLogLevelForPackage("com.zaxxer.hikari", Level.INFO)
            configureLogLevelForPackage("io.mockk.impl", Level.INFO)
        }.build()
    }

    private fun ConfigurationBuilder<*>.configureLogLevelForPackage(packageName: String, level: Level) {
        add(
            newLogger(packageName, level)
                .add(newAppenderRef(appenderRef))
                .addAttribute("additivity", false)
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
                arrayOf(AppenderRef.createAppenderRef(appenderRef, null, null)),
                null,
                loggerContext.configuration,
                null
            )
        )
    }

    fun initialize(ctx: Micro) {
        if (ctx.developmentModeEnabled || ctx.commandLineArguments.contains("--debug")) {
            loggerContext.configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).level = Level.DEBUG
        }

        loggerContext.updateLoggers()
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
