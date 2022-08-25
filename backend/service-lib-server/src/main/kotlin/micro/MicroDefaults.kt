package dk.sdu.cloud.micro

import org.apache.logging.log4j.core.config.ConfigurationFactory

fun Micro(): Micro {
    // Hack for backwards compatibility
    return Micro(null) { ConfigurationFactory.setConfigurationFactory(Log4j2ConfigFactory) }
}
