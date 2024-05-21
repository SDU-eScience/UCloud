package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.events.RedisConnectionManager
import dk.sdu.cloud.events.RedisStreamService
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.findValidHostname
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicBoolean

data class RedisConfiguration(
    val hostname: String? = null,
    val port: Int? = null,
    val password: String? = null,
    val tls: Boolean? = null,
)

class RedisFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(ConfigurationFeature)
        val shouldLog = didLog.compareAndSet(false, true)
        if (shouldLog) log.trace("Connecting to redis")

        val userConfig = ctx.configuration.requestChunkAtOrNull("redis") ?: RedisConfiguration()
        val hostname = userConfig.hostname?.takeIf { it.isNotEmpty() } ?: run {
            if (shouldLog) log.trace("No available configuration found at 'redis/hostname'.")
            if (shouldLog) log.trace("Attempting to look for defaults.")

            val hostname = findValidHostname(defaultHostNames)
                ?: throw IllegalStateException("Could not find a valid redis host")

            if (shouldLog) log.trace("$hostname is a valid host, assuming redis is running on this machine.")

            hostname
        }

        if (shouldLog) log.info("Connected to redis at $hostname. Config is loaded from redis/hostname.")

        val port = userConfig.port ?: 6379
        ctx.redisConnectionManager = RedisConnectionManager(RedisClient.create(
            RedisURI.create(
                buildString {
                    append("redis")
                    if (userConfig.tls == true) append("s")
                    append("://")
                    if (userConfig.password != null) {
                        append(userConfig.password)
                        append("@")
                    }
                    append(hostname)
                    append(":")
                    append(port)
                }
            ).also { cfg ->
                if (userConfig.tls == true) {
                    // NOTE(Dan): We do not support TLS peer verification at the moment
                    cfg.setVerifyPeer(false)
                }
            }
        ))

        ctx.eventStreamService = RedisStreamService(
            ctx.redisConnectionManager,
            ctx.serviceDescription.name,
            ctx.serviceInstance.hostname
        )

        ctx.featureOrNull(DeinitFeature)?.addHandler {
            ctx.eventStreamService.stop()
        }
    }

    companion object Feature : MicroFeatureFactory<RedisFeature, Unit>, Loggable {
        override val log: Logger = logger()
        override val key: MicroAttributeKey<RedisFeature> = MicroAttributeKey("redis-feature")
        override fun create(config: Unit): RedisFeature = RedisFeature()

        private val defaultHostNames = listOf("redis", "localhost")
        private val didLog = AtomicBoolean(false)
    }
}

private val redisConnectionManagerKey = MicroAttributeKey<RedisConnectionManager>("redis-connection-manager")
var Micro.redisConnectionManager: RedisConnectionManager
    get() {
        return attributes[redisConnectionManagerKey]
    }
    set(value) {
        attributes[redisConnectionManagerKey] = value
    }

